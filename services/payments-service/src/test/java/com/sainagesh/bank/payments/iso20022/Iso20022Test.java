package com.sainagesh.bank.payments.iso20022;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sainagesh.bank.money.Money;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class Iso20022Test {

    private static final Instant NOW = Instant.parse("2026-10-05T14:00:00Z");

    private static Pacs008 pacs008(String creditorName, String remittance) {
        return new Pacs008(
                "MSG-1",
                NOW,
                "INSTR-1",
                "INV-42",
                "TX-1",
                Money.of("125.50", "USD"),
                LocalDate.of(2026, 10, 5),
                "Account holder",
                "11111111-2222-3333-4444-555555555555",
                "999000017",
                creditorName,
                "123456789",
                "021000021",
                remittance);
    }

    @Test
    void aPacs008SurvivesBeingWrittenAndReadBack() {
        Pacs008 message = pacs008("Jane Doe", "Invoice 42");

        String xml = message.toXml();

        assertTrue(xml.contains("xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08\""), xml);
        assertTrue(xml.contains("<IntrBkSttlmAmt Ccy=\"USD\">125.50</IntrBkSttlmAmt>"), xml);
        assertEquals(message, Pacs008.parse(xml));
    }

    @Test
    void namesWithSpecialCharactersAreEscapedSoTheyCannotBreakTheMessage() {
        Pacs008 message = pacs008("Smith & Sons <Ltd> \"Quotes\"", "5 < 6 & 7 > 6");

        String xml = message.toXml();

        assertTrue(xml.contains("Smith &amp; Sons &lt;Ltd&gt; &quot;Quotes&quot;"), xml);
        assertEquals(message, Pacs008.parse(xml));
    }

    @Test
    void charactersXmlDoesNotAllowAreLeftOutSoTheMessageCanStillBeRead() {
        // A control character, a "not a character" value and half of a surrogate pair.
        Pacs008 message = pacs008("Bob\u0001\uFFFF\uD83D Ltd", null);

        String xml = message.toXml();

        assertEquals("Bob Ltd", Pacs008.parse(xml).creditorName());
    }

    @Test
    void aWholeEmojiIsKept() {
        Pacs008 message = pacs008("Caf\u00e9 \uD83D\uDE00", null);

        assertEquals("Caf\u00e9 \uD83D\uDE00", Pacs008.parse(message.toXml()).creditorName());
    }

    @Test
    void optionalFieldsAreLeftOutWhenEmpty() {
        String xml = pacs008("Jane Doe", null).toXml();

        assertFalse(xml.contains("RmtInf"), xml);
        assertNull(Pacs008.parse(xml).remittanceInformation());
    }

    @Test
    void aPacs002SaysSettledOrRejectedWithAReason() {
        Pacs002 settled = new Pacs002("NET-1", NOW, "MSG-1", "INV-42", "TX-1", "ACSC", null, null);
        Pacs002 rejected = new Pacs002("NET-2", NOW, "MSG-1", "INV-42", "TX-1", "RJCT", "AC04", "The account is closed");

        assertEquals(settled, Pacs002.parse(settled.toXml()));
        assertTrue(Pacs002.parse(settled.toXml()).settled());

        Pacs002 read = Pacs002.parse(rejected.toXml());
        assertEquals(rejected, read);
        assertFalse(read.settled());
        assertEquals("AC04", read.reasonCode());
    }

    private static final String PAIN_001 = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pain.001.001.09">
              <CstmrCdtTrfInitn>
                <GrpHdr><MsgId>ACME-2026-0001</MsgId><CreDtTm>2026-10-05T14:00:00Z</CreDtTm><NbOfTxs>1</NbOfTxs></GrpHdr>
                <PmtInf>
                  <PmtInfId>BATCH-1</PmtInfId>
                  <PmtMtd>TRF</PmtMtd>
                  <PmtTpInf><LclInstrm><Prtry>INSTANT</Prtry></LclInstrm></PmtTpInf>
                  <Dbtr><Nm>Acme Corp</Nm></Dbtr>
                  <DbtrAcct><Id><Othr><Id>11111111-2222-3333-4444-555555555555</Id></Othr></Id></DbtrAcct>
                  <CdtTrfTxInf>
                    <PmtId><EndToEndId>INV-42</EndToEndId></PmtId>
                    <Amt><InstdAmt Ccy="USD">980.00</InstdAmt></Amt>
                    <CdtrAgt><FinInstnId><ClrSysMmbId><MmbId>021000021</MmbId></ClrSysMmbId></FinInstnId></CdtrAgt>
                    <Cdtr><Nm>Supplier Inc</Nm></Cdtr>
                    <CdtrAcct><Id><Othr><Id>987654321</Id></Othr></Id></CdtrAcct>
                    <RmtInf><Ustrd>Invoice 42</Ustrd></RmtInf>
                  </CdtTrfTxInf>
                </PmtInf>
              </CstmrCdtTrfInitn>
            </Document>
            """;

    @Test
    void aPain001FromACustomerIsRead() {
        Pain001 message = Pain001.parse(PAIN_001);

        assertEquals("ACME-2026-0001", message.messageId());
        assertEquals("11111111-2222-3333-4444-555555555555", message.debtorAccount());
        assertEquals("Supplier Inc", message.creditorName());
        assertEquals("987654321", message.creditorAccount());
        assertEquals("021000021", message.creditorAgent());
        assertEquals(Money.of("980.00", "USD"), message.amount());
        assertEquals("INV-42", message.endToEndId());
        assertEquals("Invoice 42", message.remittanceInformation());
        assertEquals("INSTANT", message.localInstrument());
    }

    @Test
    void aPain001ThatNamesTheAmountOrTheCreditorTwiceIsRefused() {
        String twoAmounts = PAIN_001.replace(
                "<Amt><InstdAmt Ccy=\"USD\">980.00</InstdAmt></Amt>",
                "<Amt><InstdAmt Ccy=\"USD\">980.00</InstdAmt><InstdAmt Ccy=\"USD\">1.00</InstdAmt></Amt>");
        String twoCreditors =
                PAIN_001.replace("<Cdtr><Nm>Supplier Inc</Nm></Cdtr>", "<Cdtr><Nm>Supplier Inc</Nm></Cdtr><Cdtr><Nm>Someone Else</Nm></Cdtr>");

        assertThrows(MessageFormatException.class, () -> Pain001.parse(twoAmounts));
        assertThrows(MessageFormatException.class, () -> Pain001.parse(twoCreditors));
    }

    @Test
    void aMessageWithAMissingFieldSaysWhichOne() {
        String xml = pacs008("Jane Doe", null).toXml().replace("<TxId>TX-1</TxId>", "");

        MessageFormatException e = assertThrows(MessageFormatException.class, () -> Pacs008.parse(xml));

        assertEquals("The message has no PmtId/TxId", e.getMessage());
    }

    @Test
    void textThatIsNotXmlIsRefused() {
        assertThrows(MessageFormatException.class, () -> Pacs008.parse("not xml at all"));
        assertThrows(MessageFormatException.class, () -> Pacs002.parse(pacs008("Jane", null).toXml()));
    }

    /** The classic attack on XML readers. The message tries to make the server read one of its own files. */
    @Test
    void aMessageThatTriesToReadServerFilesIsRefused() {
        String attack = """
                <?xml version="1.0"?>
                <!DOCTYPE Document [<!ENTITY secret SYSTEM "file:///etc/passwd">]>
                <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
                  <FIToFICstmrCdtTrf><GrpHdr><MsgId>&secret;</MsgId></GrpHdr></FIToFICstmrCdtTrf>
                </Document>
                """;

        assertThrows(MessageFormatException.class, () -> Pacs008.parse(attack));
    }
}
