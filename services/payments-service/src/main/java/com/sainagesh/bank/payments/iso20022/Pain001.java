package com.sainagesh.bank.payments.iso20022;

import com.sainagesh.bank.money.Money;
import java.util.regex.Pattern;
import org.w3c.dom.Element;

/**
 * pain.001, "Customer Credit Transfer Initiation". A customer, usually a company's own system, asks
 * its bank to send a payment. Only a message with one payment in it is read here.
 *
 * @param debtorAccount   the account to pay from. In this platform it is an account id
 * @param localInstrument which rail the customer asks for (BOOK, ACH or INSTANT). Null means ACH
 */
public record Pain001(
        String messageId,
        String debtorAccount,
        String creditorName,
        String creditorAccount,
        String creditorAgent,
        Money amount,
        String endToEndId,
        String remittanceInformation,
        String localInstrument) {

    public static final String TYPE = "pain.001.001.09";

    /** Up to fifteen digits and five decimals, the limits ISO 20022 sets for an amount. */
    private static final Pattern AMOUNT = Pattern.compile("[0-9]{1,15}([.][0-9]{1,5})?");

    private static final Pattern CURRENCY = Pattern.compile("[A-Z]{3}");

    public static Pain001 parse(String xml) {
        Element body = XmlReader.at(XmlReader.parse(xml), "CstmrCdtTrfInitn");
        if (body == null) {
            throw new MessageFormatException("This is not a pain.001 message");
        }
        Element info = XmlReader.at(body, "PmtInf");
        // A real pain.001 can carry many payments. This API takes one at a time, so a message with
        // more is refused whole. Taking the first and dropping the rest in silence would be worse.
        if (XmlReader.count(body, "PmtInf") > 1 || XmlReader.count(info, "CdtTrfTxInf") > 1) {
            throw new MessageFormatException("The message holds more than one payment. Send one payment in each message");
        }
        Element tx = XmlReader.at(info, "CdtTrfTxInf");
        // The reader takes the first element with a name. So the ones that say who is paid and how
        // much must appear once. A second copy with other values could hide what the sender meant.
        if (XmlReader.count(info, "DbtrAcct") > 1
                || XmlReader.count(tx, "Amt") > 1
                || XmlReader.count(XmlReader.at(tx, "Amt"), "InstdAmt") > 1
                || XmlReader.count(tx, "Cdtr") > 1
                || XmlReader.count(tx, "CdtrAcct") > 1) {
            throw new MessageFormatException("The message gives the debtor, the creditor or the amount more than once");
        }
        Element amount = XmlReader.at(tx, "Amt", "InstdAmt");
        if (amount == null) {
            throw new MessageFormatException("The message has no PmtInf/CdtTrfTxInf/Amt/InstdAmt");
        }
        String digits = amount.getTextContent().trim();
        String currency = amount.getAttribute("Ccy");
        // Check the shape before any arithmetic. "1E400000000" is a number to BigDecimal, and working
        // it out would use up the memory of the service.
        if (!AMOUNT.matcher(digits).matches() || !CURRENCY.matcher(currency).matches()) {
            throw new MessageFormatException("The amount in the message is not valid");
        }
        Money money;
        try {
            money = Money.of(digits, currency);
        } catch (RuntimeException e) {
            throw new MessageFormatException("The amount in the message is not valid", e);
        }
        String endToEndId = XmlReader.text(tx, "PmtId", "EndToEndId");
        return new Pain001(
                XmlReader.required(body, "GrpHdr", "MsgId"),
                XmlReader.required(info, "DbtrAcct", "Id", "Othr", "Id"),
                XmlReader.required(tx, "Cdtr", "Nm"),
                XmlReader.required(tx, "CdtrAcct", "Id", "Othr", "Id"),
                XmlReader.text(tx, "CdtrAgt", "FinInstnId", "ClrSysMmbId", "MmbId"),
                money,
                "NOTPROVIDED".equals(endToEndId) ? null : endToEndId,
                XmlReader.text(tx, "RmtInf", "Ustrd"),
                XmlReader.text(info, "PmtTpInf", "LclInstrm", "Prtry"));
    }
}
