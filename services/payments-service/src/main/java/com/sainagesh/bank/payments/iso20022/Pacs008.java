package com.sainagesh.bank.payments.iso20022;

import com.sainagesh.bank.money.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.w3c.dom.Element;

/**
 * pacs.008, "FI to FI Customer Credit Transfer". One bank tells another to credit a customer.
 * This is the message that travels on FedNow, RTP and SWIFT.
 *
 * @param messageId       id of this message, given by the sender
 * @param instructionId   the sending bank's id for the payment
 * @param endToEndId      the customer's own reference. It travels unchanged to the receiver
 * @param transactionId   the id both banks use to talk about this payment afterwards
 * @param debtorAgent     the routing number of the sending bank
 * @param creditorAgent   the routing number of the receiving bank
 */
public record Pacs008(
        String messageId,
        Instant createdAt,
        String instructionId,
        String endToEndId,
        String transactionId,
        Money amount,
        LocalDate settlementDate,
        String debtorName,
        String debtorAccount,
        String debtorAgent,
        String creditorName,
        String creditorAccount,
        String creditorAgent,
        String remittanceInformation) {

    public static final String TYPE = "pacs.008.001.08";
    private static final String NAMESPACE = "urn:iso:std:iso:20022:tech:xsd:" + TYPE;

    public String toXml() {
        XmlWriter xml = new XmlWriter()
                .open("Document", "xmlns", NAMESPACE)
                .open("FIToFICstmrCdtTrf")
                .open("GrpHdr")
                .text("MsgId", messageId)
                .text("CreDtTm", createdAt.truncatedTo(ChronoUnit.SECONDS).toString())
                .text("NbOfTxs", "1")
                .open("SttlmInf")
                .text("SttlmMtd", "CLRG")
                .close()
                .close()
                .open("CdtTrfTxInf")
                .open("PmtId")
                .text("InstrId", instructionId)
                .text("EndToEndId", endToEndId == null ? "NOTPROVIDED" : endToEndId)
                .text("TxId", transactionId)
                .close()
                .text("IntrBkSttlmAmt", amount.amount().toPlainString(), "Ccy", amount.currency().getCurrencyCode())
                .text("IntrBkSttlmDt", settlementDate.toString())
                .text("ChrgBr", "SLEV")
                .open("Dbtr")
                .text("Nm", debtorName)
                .close()
                .open("DbtrAcct")
                .open("Id")
                .open("Othr")
                .text("Id", debtorAccount)
                .close()
                .close()
                .close()
                .open("DbtrAgt")
                .open("FinInstnId")
                .open("ClrSysMmbId")
                .text("MmbId", debtorAgent)
                .close()
                .close()
                .close()
                .open("CdtrAgt")
                .open("FinInstnId")
                .open("ClrSysMmbId")
                .text("MmbId", creditorAgent)
                .close()
                .close()
                .close()
                .open("Cdtr")
                .text("Nm", creditorName)
                .close()
                .open("CdtrAcct")
                .open("Id")
                .open("Othr")
                .text("Id", creditorAccount)
                .close()
                .close()
                .close();
        if (remittanceInformation != null && !remittanceInformation.isBlank()) {
            xml.open("RmtInf").text("Ustrd", remittanceInformation).close();
        }
        return xml.finish();
    }

    public static Pacs008 parse(String xml) {
        Element body = XmlReader.at(XmlReader.parse(xml), "FIToFICstmrCdtTrf");
        if (body == null) {
            throw new MessageFormatException("This is not a pacs.008 message");
        }
        Element tx = XmlReader.at(body, "CdtTrfTxInf");
        Element amount = XmlReader.at(tx, "IntrBkSttlmAmt");
        if (amount == null) {
            throw new MessageFormatException("The message has no CdtTrfTxInf/IntrBkSttlmAmt");
        }
        Money money;
        try {
            money = Money.of(amount.getTextContent().trim(), amount.getAttribute("Ccy"));
        } catch (RuntimeException e) {
            throw new MessageFormatException("The amount in the message is not valid", e);
        }
        String date = XmlReader.text(tx, "IntrBkSttlmDt");
        return new Pacs008(
                XmlReader.required(body, "GrpHdr", "MsgId"),
                Instant.parse(XmlReader.required(body, "GrpHdr", "CreDtTm")),
                XmlReader.text(tx, "PmtId", "InstrId"),
                XmlReader.text(tx, "PmtId", "EndToEndId"),
                XmlReader.required(tx, "PmtId", "TxId"),
                money,
                date == null ? null : LocalDate.parse(date),
                XmlReader.text(tx, "Dbtr", "Nm"),
                XmlReader.text(tx, "DbtrAcct", "Id", "Othr", "Id"),
                XmlReader.text(tx, "DbtrAgt", "FinInstnId", "ClrSysMmbId", "MmbId"),
                XmlReader.text(tx, "Cdtr", "Nm"),
                XmlReader.required(tx, "CdtrAcct", "Id", "Othr", "Id"),
                XmlReader.text(tx, "CdtrAgt", "FinInstnId", "ClrSysMmbId", "MmbId"),
                XmlReader.text(tx, "RmtInf", "Ustrd"));
    }
}
