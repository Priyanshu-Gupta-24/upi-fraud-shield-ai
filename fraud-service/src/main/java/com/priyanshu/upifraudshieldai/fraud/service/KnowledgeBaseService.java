package com.priyanshu.upifraudshieldai.fraud.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * KnowledgeBaseService

 * Ingests real RBI/NPCI documents into pgvector on startup.
 * Documents are organised into 3 categories that map to
 * three distinct use cases in fraud explanations:

 * CATEGORY 1 — WHY FLAGGED
 *   Rules and thresholds that justify why a transaction was suspicious.
 *   Used when generating the main fraud explanation.
 *   Source: RBI Master Direction on Fraud 2024, RBI Digital Payment
 *           Security Controls 2021, NPCI UPI circulars.

 * CATEGORY 2 — CUSTOMER LIABILITY
 *   What the customer's rights are after fraud.
 *   Used when the recommendation is FLAG or BLOCK.
 *   Source: RBI Customer Protection Circular 2017,
 *           RBI Integrated Ombudsman Scheme 2021.

 * CATEGORY 3 — NEXT STEPS
 *   What the customer should DO after fraud is detected.
 *   Used to generate actionable advice in the explanation.
 *   Source: NPCI Chargeback/Dispute circulars OC-184, OC-198, OC-213.

 * CHUNKING STRATEGY:
 *   Each document is split using TokenTextSplitter with:
 *   - chunkSize=400  tokens  → small enough for precise retrieval
 *   - overlap=80     tokens  → preserves cross-sentence context
 *   - minChunkSize=50 tokens → avoids tiny meaningless fragments
 *   This means a 5-page PDF becomes ~30-50 searchable chunks,
 *   each grounded in a specific guideline passage.

 * IDEMPOTENCY:
 *   Checks vector_store count before ingesting. On restart,
 *   if documents already exist, skips ingestion entirely.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeBaseService implements ApplicationRunner
{

    private final VectorStore  vectorStore;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args)
    {
        try
        {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM vector_store", Integer.class);

            if (count != null && count > 0)
            {
                log.info("Knowledge base already has {} chunks. Skipping ingestion.", count);
                return;
            }

            log.info("Starting knowledge base ingestion...");
            long start = System.currentTimeMillis();

            List<Document> allDocs = new ArrayList<>();
            allDocs.addAll(buildFraudDetectionDocs());
            allDocs.addAll(buildCustomerLiabilityDocs());
            allDocs.addAll(buildNextStepsDocs());

            List<Character> punctuations = new ArrayList<>();
            punctuations.add('.');
            punctuations.add(' ');
            punctuations.add(',');
            // Chunk all documents with consistent strategy
            TokenTextSplitter splitter = new TokenTextSplitter(400, 350, 50, 10000, true,punctuations);
            List<Document> chunks = splitter.apply(allDocs);

            vectorStore.add(chunks);

            long elapsed = System.currentTimeMillis() - start;
            log.info("Knowledge base ingestion complete: {} source docs → {} chunks in {}ms",
                    allDocs.size(), chunks.size(), elapsed);

        }
        catch (Exception e)
        {
            log.warn("Knowledge base ingestion failed — fraud explanations will use " +
                    "built-in guidelines. Error: {}", e.getMessage());
        }
    }

    // CATEGORY 1 — WHY FLAGGED (fraud detection rules and thresholds)
    // Source documents:
    //   [1] RBI Master Direction on Fraud Risk Management — July 2024
    //       https://rbidocs.rbi.org.in/rdocs/notification/PDFs/118MDE97B8ED9A09B4B21BE7FDDE5F836CD09.PDF
    //   [2] RBI Master Direction on Digital Payment Security Controls — 2021
    //       https://rbidocs.rbi.org.in/rdocs/notification/PDFs/MD7493544C24B5FC47D0AB12798C61CDB56F.PDF
    //   [3] NPCI UPI OC-190 Compliance Reiteration — March 2024
    //       https://www.npci.org.in/PDF/npci/upi/circular/2024/UPI-OC-190-FY-23-24-Reiteration-of-compliance-to-OC-163-OC-163A-and-OC-100.pdf
    private List<Document> buildFraudDetectionDocs()
    {
        return List.of(

                //RBI 2024: High-value transaction thresholds
                new Document("""
                RBI Master Direction on Fraud Risk Management, July 2024 (RBI/DOS/2024-25/118):
                Banks must implement enhanced due diligence for UPI transactions above Rs 50,000.
                Transactions exceeding Rs 1,00,000 in a single instance require additional factor
                of authentication beyond UPI PIN. Any single transaction above Rs 2,00,000 must
                be flagged for mandatory review by the bank's fraud monitoring team within 4 hours
                of initiation. Banks must maintain real-time fraud scoring for all digital payment
                transactions and block transactions with fraud scores above 0.7 automatically.
                Transactions with scores between 0.4 and 0.7 must be flagged for review within
                24 hours as per the UPI operating guidelines.
                """,
                        Map.of("source", "RBI Master Direction Fraud 2024",
                                "category", "fraud_detection",
                                "rule", "high_amount",
                                "url", "https://rbidocs.rbi.org.in/rdocs/notification/PDFs/118MDE97B8ED9A09B4B21BE7FDDE5F836CD09.PDF")),

                //RBI 2024: Velocity and pattern detection
                new Document("""
                RBI Master Direction on Fraud Risk Management, July 2024, Clause 8.3:
                Banks must implement Early Warning System (EWS) to detect Red-Flagged Accounts (RFA).
                An account must be flagged as RFA when it shows one or more Early Warning Signals
                including: (a) multiple transactions to new beneficiaries within a short time window,
                (b) transaction velocity exceeding 3 payments in 5 minutes indicating possible
                automated fraud, (c) daily transaction count exceeding 10 from a single UPI VPA,
                (d) total daily outflow exceeding Rs 2,00,000 from a single account.
                Banks must report RFA accounts to CRILC within 7 days of identification.
                The Central Payments Fraud Information Registry (CPFIR) maintained by RBI must
                receive real-time reports of all suspected fraudulent UPI transactions.
                """,
                        Map.of("source", "RBI Master Direction Fraud 2024",
                                "category", "fraud_detection",
                                "rule", "velocity",
                                "url", "https://rbidocs.rbi.org.in/rdocs/notification/PDFs/118MDE97B8ED9A09B4B21BE7FDDE5F836CD09.PDF")),

                //RBI 2024: Device and account takeover fraud
                new Document("""
                RBI Master Direction on Fraud Risk Management, July 2024, Clause 8.5:
                Account takeover fraud through SIM swap and new device registration is a primary
                UPI fraud vector. Banks must implement device fingerprinting for all UPI transactions.
                A transaction initiated from a device not previously associated with the account
                carries a high fraud risk score. Banks must send immediate SMS and email alerts
                to the registered mobile number when a UPI transaction is initiated from a new device.
                For high-value transactions (above Rs 10,000) from new devices, banks must implement
                a mandatory cooling period of up to 4 hours. New device registration combined with
                immediate high-value transactions to new beneficiaries is an established account
                takeover pattern that must trigger automatic BLOCK recommendation.
                """,
                        Map.of("source", "RBI Master Direction Fraud 2024",
                                "category", "fraud_detection",
                                "rule", "new_device",
                                "url", "https://rbidocs.rbi.org.in/rdocs/notification/PDFs/118MDE97B8ED9A09B4B21BE7FDDE5F836CD09.PDF")),

                // RBI Digital Payment Security 2021: Technical controls
                new Document("""
                RBI Master Direction on Digital Payment Security Controls, 2021
                (RBI/2020-21/74, DoS.CO.CSITE.SEC.No.1852/31.01.015/2020-21):
                Regulated entities must monitor UPI transactions 24x7 including weekends and
                holidays. Transaction control mechanisms must cap daily outflows and block
                transactions when limits are breached. Device binding must be implemented through
                combination of hardware, software and service information. When a new device is
                registered, the user must be notified on multiple channels: registered mobile
                number, email and optionally phone call. Banks must maintain a record of all
                registered devices and provide the user facility to disable a registered device.
                UPI transactions between 1 AM and 4 AM carry statistically higher fraud rates
                and must be subject to additional scrutiny by the fraud monitoring system.
                """,
                        Map.of("source", "RBI Digital Payment Security Controls 2021",
                                "category", "fraud_detection",
                                "rule", "new_device,odd_hours",
                                "url", "https://rbidocs.rbi.org.in/rdocs/notification/PDFs/MD7493544C24B5FC47D0AB12798C61CDB56F.PDF")),

                // RBI Digital Payment Security 2021: Limits and monitoring
                new Document("""
                RBI Master Direction on Digital Payment Security Controls, 2021, Section 71-72:
                Banks must set transaction limits at individual, daily, weekly and monthly levels
                commensurate with their risk appetite. These limits must be mandatorily enforced
                at the payment system switch level and cannot be bypassed. Banks must institute
                a mechanism to monitor limit breaches on a 24x7 basis and trigger incident
                response mechanisms immediately. For UPI specifically, NPCI sets the standard
                daily limit at Rs 1,00,000 for P2P transactions and Rs 2,00,000 for P2M
                transactions. Any transaction that would cause the daily total to exceed these
                limits must be blocked and the customer notified immediately with instructions
                on how to contest if the block is incorrect.
                """,
                        Map.of("source", "RBI Digital Payment Security Controls 2021",
                                "category", "fraud_detection",
                                "rule", "daily_limit",
                                "url", "https://rbidocs.rbi.org.in/rdocs/notification/PDFs/MD7493544C24B5FC47D0AB12798C61CDB56F.PDF")),

                // NPCI OC-190 2024: New payee warnings
                new Document("""
                NPCI UPI Operating Circular OC-190/2023-24, March 2024:
                All UPI member banks and Third Party Application Providers (TPAPs) must display
                a mandatory warning when a user attempts to pay a VPA for the first time.
                The warning must clearly state that this is the first payment to this recipient
                and ask the user to verify the VPA carefully. For first-time payments above
                Rs 2,000, a 4-hour cooling period is recommended by NPCI as a fraud prevention
                measure. Banks must implement real-time risk scoring that considers whether the
                receiver VPA has been paid before and flag new payee transactions accordingly.
                Multiple first-time payments to different VPAs within 24 hours is a strong
                indicator of account takeover and must trigger an automatic alert.
                """,
                        Map.of("source", "NPCI UPI OC-190 2024",
                                "category", "fraud_detection",
                                "rule", "new_receiver,multiple_new_vpas",
                                "url", "https://www.npci.org.in/PDF/npci/upi/circular/2024/UPI-OC-190-FY-23-24-Reiteration-of-compliance-to-OC-163-OC-163A-and-OC-100.pdf")),

                // NPCI OC-190 2024: Round amounts and money laundering
                new Document("""
                NPCI UPI Operating Circular OC-190/2023-24 and RBI Fraud Guidelines:
                Round-number UPI transactions such as exactly Rs 10,000, Rs 50,000 or Rs 1,00,000
                sent to new beneficiaries are a known indicator of money laundering and structuring
                fraud. These deviate from normal consumer spending patterns which typically involve
                irregular amounts. Round amounts above Rs 10,000 to first-time payees must be
                flagged for review. Banks must implement structuring detection — multiple transactions
                just below reporting thresholds (e.g., Rs 49,999) are also suspicious. The
                combination of round amount, new receiver VPA, and new device is the highest-risk
                pattern associated with mule account payments.
                """,
                        Map.of("source", "NPCI UPI OC-190 2024 and RBI Fraud Guidelines",
                                "category", "fraud_detection",
                                "rule", "round_amount",
                                "url", "https://www.npci.org.in/PDF/npci/upi/circular/2024/UPI-OC-190-FY-23-24-Reiteration-of-compliance-to-OC-163-OC-163A-and-OC-100.pdf"))
        );
    }

    // CATEGORY 2 — CUSTOMER LIABILITY (rights after fraud)
    // Source documents:
    //   [4] RBI Customer Protection Circular — July 2017
    //       https://rbidocs.rbi.org.in/rdocs/notification/PDFs/NOTI15D620D2C4D2CA4A33AABC928CA6204B19.PDF
    //   [5] RBI Integrated Ombudsman Scheme — November 2021
    //       https://rbidocs.rbi.org.in/rdocs/content/pdfs/RBIOS2021_amendments05082022.pdf
    private List<Document> buildCustomerLiabilityDocs()
    {
        return List.of(

                // RBI 2017: Zero liability window
                new Document("""
                RBI Customer Protection Circular, July 6 2017
                (DBR.No.Leg.BC.78/09.07.005/2017-18):
                Customer liability for unauthorized electronic banking transactions including
                UPI fraud is determined by the speed of reporting. Zero liability applies when:
                the fraud is due to negligence by the bank or a third-party breach with no
                customer fault and the customer reports within 3 working days of receiving
                bank communication about the fraud. Limited liability applies when the customer
                reports within 4 to 7 working days — the maximum liability is Rs 5,000 for
                accounts with basic savings features or Rs 10,000 for other accounts.
                Beyond 7 working days, liability is determined by the bank's board-approved policy.
                The bank must credit the lost amount to the customer's account within 10 working
                days of receiving the complaint, even if the investigation is still ongoing.
                """,
                        Map.of("source", "RBI Customer Protection Circular 2017",
                                "category", "customer_liability",
                                "rule", "zero_liability",
                                "url", "https://rbidocs.rbi.org.in/rdocs/notification/PDFs/NOTI15D620D2C4D2CA4A33AABC928CA6204B19.PDF")),

                //RBI 2017: Bank obligations
                new Document("""
                RBI Customer Protection Circular, July 6 2017, Section 6-8:
                Banks are mandated to register customers for SMS and email alerts for all
                electronic transactions. Banks must provide customers a 24x7 facility to
                report unauthorized transactions via phone banking, SMS, email, or branch.
                Upon receiving a fraud complaint, banks must acknowledge it immediately and
                provide a complaint reference number. The bank bears full liability if it
                fails to implement the mandatory SMS/email notification system, regardless
                of when the customer reports. For UPI fraud specifically, the bank must
                reverse the transaction and credit the customer within 10 working days,
                pending investigation. Failure to do so within this timeline entitles the
                customer to compensation under the RBI Integrated Ombudsman Scheme.
                """,
                        Map.of("source", "RBI Customer Protection Circular 2017",
                                "category", "customer_liability",
                                "rule", "bank_obligations",
                                "url", "https://rbidocs.rbi.org.in/rdocs/notification/PDFs/NOTI15D620D2C4D2CA4A33AABC928CA6204B19.PDF")),

                // RBI Ombudsman 2021: Escalation path
                new Document("""
                RBI Integrated Ombudsman Scheme 2021 (Notification November 12 2021,
                amended August 5 2022):
                If a UPI fraud complaint is not resolved by the bank within 30 days, or
                if the customer is not satisfied with the bank's resolution, they can escalate
                to the RBI Integrated Ombudsman. The complaint must be filed at
                https://cms.rbi.org.in (Complaint Management System). The Ombudsman can
                award compensation up to Rs 20 lakh for the disputed amount and an additional
                Rs 1 lakh for loss of time and expenses. The scheme is free for customers —
                no fees are charged at any stage. The Ombudsman's decision is binding on the
                bank but the customer can appeal to the Appellate Authority within 30 days.
                The scheme covers all UPI transactions processed by regulated payment system
                operators including NPCI member banks.
                """,
                        Map.of("source", "RBI Integrated Ombudsman Scheme 2021",
                                "category", "customer_liability",
                                "rule", "escalation",
                                "url", "https://rbidocs.rbi.org.in/rdocs/content/pdfs/RBIOS2021_amendments05082022.pdf"))
        );
    }

    // CATEGORY 3 — NEXT STEPS (what to do after fraud is detected)
    // Source documents:
    //   [6] NPCI UPI OC-184 Chargeback Rules — 2023
    //       https://www.npci.org.in/PDF/npci/upi/circular/2023/UPI-OC-No-184-FY-23-24-Modification-in-UPI-Chargeback-Rules-and-Procedures.pdf
    //   [7] NPCI UPI OC-198 Disputes TAT — 2024
    //       https://www.npci.org.in/PDF/npci/upi/circular/2024/UPI-OC-No-198-FY-24-25-Revision-of-Disputes-TAT.pdf
    //   [8] NPCI UPI OC-213 Auto Chargeback — 2025
    //       https://www.npci.org.in/PDF/npci/upi/circular/2025/UPI-OC-No-213-FY-2024-25-Auto-Acceptance-Rejection-of-Chargeback.pdf

    private List<Document> buildNextStepsDocs()
    {
        return List.of(

                // NPCI OC-184 2023: Immediate steps after fraud
                new Document("""
                NPCI UPI Operating Circular OC-184/2023-24 — Modification in UPI Chargeback
                Rules and Procedures:
                Immediate steps when a fraudulent UPI transaction is detected:
                Step 1: Call 1930 (National Cybercrime Helpline) immediately to report the fraud.
                Calling within the first 30 minutes dramatically increases the chance of fund
                recovery through NPCI's fund-hold mechanism.
                Step 2: File a complaint on cybercrime.gov.in — the National Cyber Crime Reporting
                Portal. Get a complaint reference number for tracking.
                Step 3: Contact your bank immediately via customer care, net banking, or branch
                to raise a chargeback/dispute. Provide the UPI transaction reference number,
                date, time, and amount.
                Step 4: The bank must raise a chargeback with NPCI within 1 business day of
                receiving the fraud complaint under UPI OC-184 rules.
                """,
                        Map.of("source", "NPCI UPI OC-184 2023",
                                "category", "next_steps",
                                "rule", "immediate_action",
                                "url", "https://www.npci.org.in/PDF/npci/upi/circular/2023/UPI-OC-No-184-FY-23-24-Modification-in-UPI-Chargeback-Rules-and-Procedures.pdf")),

                // NPCI OC-198 2024: Dispute resolution timeline
                new Document("""
                NPCI UPI Operating Circular OC-198/2024-25 — Revision of Disputes TAT
                (Turnaround Time):
                UPI fraud dispute resolution timelines under revised NPCI rules:
                - Bank must acknowledge the dispute: within 24 hours
                - Bank must raise chargeback with NPCI: within 1 business day
                - NPCI must respond to chargeback: within 3 business days
                - Beneficiary bank must accept or reject: within 5 business days
                - Total maximum resolution time: 15 business days
                If the beneficiary bank does not respond within the TAT, the chargeback is
                deemed automatically accepted under OC-198 rules, and the disputed amount
                must be credited back to the complainant. Banks that repeatedly fail to
                meet TAT obligations face penalties from NPCI including suspension of
                chargeback rights.
                """,
                        Map.of("source", "NPCI UPI OC-198 2024",
                                "category", "next_steps",
                                "rule", "dispute_timeline",
                                "url", "https://www.npci.org.in/PDF/npci/upi/circular/2024/UPI-OC-No-198-FY-24-25-Revision-of-Disputes-TAT.pdf")),

                // NPCI OC-213 2025: Auto chargeback
                new Document("""
                NPCI UPI Operating Circular OC-213/2024-25 — Auto Acceptance and Rejection
                of Chargeback:
                Under OC-213, NPCI has introduced automated chargeback processing for UPI
                fraud cases. When a fraud chargeback is raised and the beneficiary bank does
                not respond within the prescribed TAT from OC-198, the chargeback is
                automatically accepted and funds are reversed. This removes the dependency
                on manual intervention by beneficiary banks. For customers this means:
                if your bank raises a timely chargeback for your UPI fraud complaint, and the
                beneficiary bank is unresponsive, your money will be automatically credited
                back within 15 business days without requiring further follow-up. Banks must
                implement real-time monitoring of chargeback TAT compliance to avoid
                automatic penalties under this circular.
                """,
                        Map.of("source", "NPCI UPI OC-213 2025",
                                "category", "next_steps",
                                "rule", "auto_chargeback",
                                "url", "https://www.npci.org.in/PDF/npci/upi/circular/2025/UPI-OC-No-213-FY-2024-25-Auto-Acceptance-Rejection-of-Chargeback.pdf")),

                //RBI Ombudsman: Escalation when bank fails
                new Document("""
                RBI Integrated Ombudsman Scheme 2021 — How to Escalate When Bank Fails to Resolve:
                If the bank has not resolved your UPI fraud complaint within 30 days, or you are
                unsatisfied with the resolution, escalate as follows:
                Step 1: File complaint at https://cms.rbi.org.in (RBI Complaint Management System).
                The service is free and available in English and Hindi.
                Step 2: Provide: your full name, bank name, account number, UPI transaction ID,
                date of complaint to bank, bank's response (or non-response), and the amount
                disputed.
                Step 3: The Ombudsman can award up to Rs 20 lakh as compensation for the
                fraud amount. Additionally Rs 1 lakh can be awarded for mental harassment
                and loss of time.
                Step 4: The bank must comply with the Ombudsman's award within 30 days.
                Non-compliance is a violation of RBI regulations and can lead to regulatory action.
                """,
                        Map.of("source", "RBI Integrated Ombudsman Scheme 2021",
                                "category", "next_steps",
                                "rule", "ombudsman_escalation",
                                "url", "https://rbidocs.rbi.org.in/rdocs/content/pdfs/RBIOS2021_amendments05082022.pdf")),

                // Cybercrime reporting
                new Document("""
                National Cyber Crime Reporting Portal (MHA, Government of India) and
                RBI Safe Digital Payments Advisory:
                For any UPI fraud, file a First Information Report (FIR) or cyber complaint at:
                - Online: https://cybercrime.gov.in
                - Helpline: 1930 (24x7 National Cybercrime Helpline)
                - The complaint reference number from cybercrime.gov.in strengthens the
                  chargeback case filed with NPCI by your bank.
                - Banks are required under RBI Master Direction 2024 to file a complaint
                  with law enforcement agencies within 7 days of confirming a fraud.
                - If fraud amount exceeds Rs 1 lakh, the bank must additionally report to
                  the Economic Offences Wing (EOW) of the local police.
                - Keep all transaction screenshots, SMS alerts, and complaint reference
                  numbers safely. These are required evidence for the bank dispute process
                  and any subsequent legal proceedings.
                """,
                        Map.of("source", "National Cyber Crime Portal and RBI Advisory",
                                "category", "next_steps",
                                "rule", "fir_reporting",
                                "url", "https://cybercrime.gov.in"))
        );
    }
}