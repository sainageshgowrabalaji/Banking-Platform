package com.sainagesh.bank.payments.iso20022;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.w3c.dom.Element;

/**
 * pacs.002, "FI to FI Payment Status Report". The receiving side says what became of a payment.
 *
 * @param status                ACSC when the money was credited, RJCT when it was refused
 * @param reasonCode            why it was refused, such as AC04 for a closed account. Null when settled
 * @param originalTransactionId the TxId of the pacs.008 this answers
 */
public record Pacs002(
        String messageId,
        Instant createdAt,
        String originalMessageId,
        String originalEndToEndId,
        String originalTransactionId,
        String status,
        String reasonCode,
        String additionalInformation) {

    public static final String TYPE = "pacs.002.001.10";
    private static final String NAMESPACE = "urn:iso:std:iso:20022:tech:xsd:" + TYPE;

    public boolean rejected() {
        return "RJCT".equals(status);
    }

    public boolean settled() {
        return "ACSC".equals(status) || "ACCP".equals(status);
    }

    public String toXml() {
        XmlWriter xml = new XmlWriter()
                .open("Document", "xmlns", NAMESPACE)
                .open("FIToFIPmtStsRpt")
                .open("GrpHdr")
                .text("MsgId", messageId)
                .text("CreDtTm", createdAt.truncatedTo(ChronoUnit.SECONDS).toString())
                .close()
                .open("OrgnlGrpInfAndSts")
                .text("OrgnlMsgId", originalMessageId)
                .text("OrgnlMsgNmId", Pacs008.TYPE)
                .close()
                .open("TxInfAndSts")
                .text("OrgnlEndToEndId", originalEndToEndId)
                .text("OrgnlTxId", originalTransactionId)
                .text("TxSts", status);
        if (reasonCode != null) {
            xml.open("StsRsnInf")
                    .open("Rsn")
                    .text("Cd", reasonCode)
                    .close()
                    .text("AddtlInf", additionalInformation)
                    .close();
        }
        return xml.finish();
    }

    public static Pacs002 parse(String xml) {
        Element body = XmlReader.at(XmlReader.parse(xml), "FIToFIPmtStsRpt");
        if (body == null) {
            throw new MessageFormatException("This is not a pacs.002 message");
        }
        Element tx = XmlReader.at(body, "TxInfAndSts");
        return new Pacs002(
                XmlReader.required(body, "GrpHdr", "MsgId"),
                Instant.parse(XmlReader.required(body, "GrpHdr", "CreDtTm")),
                XmlReader.text(body, "OrgnlGrpInfAndSts", "OrgnlMsgId"),
                XmlReader.text(tx, "OrgnlEndToEndId"),
                XmlReader.required(tx, "OrgnlTxId"),
                XmlReader.required(tx, "TxSts"),
                XmlReader.text(tx, "StsRsnInf", "Rsn", "Cd"),
                XmlReader.text(tx, "StsRsnInf", "AddtlInf"));
    }
}
