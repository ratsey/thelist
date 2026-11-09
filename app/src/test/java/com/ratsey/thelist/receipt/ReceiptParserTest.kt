package com.ratsey.thelist.receipt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ReceiptParserTest {

    private fun epochOf(date: String): Long =
        LocalDate.parse(date).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private val tesco = """
        TESCO STORES LTD
        TESCO EXPRESS
        12 HIGH STREET
        LONDON
        TEL 020 7946 0000
        VAT REG 123 4567 89
        14/03/2026  18:42
        SEMI SKIMMED MILK 2L        1.85
        HOVIS WHOLEMEAL 800G        1.45
        FREE RANGE EGGS 6PK         2.40
        BANANAS 1KG                 1.10
        CLUB SANDWICH               3.50
        TOTAL                      10.30
        CARD                       10.30
        CHANGE                      0.00
        THANK YOU FOR SHOPPING
    """.trimIndent()

    @Test
    fun `reads store, date, total and every priced line`() {
        val receipt = ReceiptParser.parse(tesco)

        assertEquals("TESCO STORES LTD", receipt.storeName)
        assertEquals(epochOf("2026-03-14"), receipt.purchasedAt)
        assertEquals("£", receipt.currency)
        assertEquals(10.30, receipt.total!!, 0.001)
        assertEquals(5, receipt.lines.size)
        assertTrue(receipt.lines.any { it.name.contains("MILK") && it.lineTotal == 1.85 })
        assertTrue(receipt.lines.any { it.name.contains("BANANAS") })
        assertEquals(
            10.30,
            receipt.lines.sumOf { it.lineTotal },
            0.001,
        )
    }

    @Test
    fun `ignores totals, card payments and change`() {
        val names = ReceiptParser.parse(tesco).lines.map { it.name.uppercase() }
        assertTrue(names.none { it.contains("TOTAL") })
        assertTrue(names.none { it.contains("CARD") })
        assertTrue(names.none { it.contains("CHANGE") })
    }

    @Test
    fun `handles quantities and comma decimals`() {
        val receipt = ReceiptParser.parse(
            """
            REWE MARKT
            Hauptstrasse 12
            15.03.2026
            2 x APFEL 0,79 €        1,58 €
            VOLLMILCH 1,5%          1,29 €
            SUMME                   2,87 €
            """.trimIndent(),
            dayFirst = true,
        )

        val apples = receipt.lines.first { it.name.contains("APFEL") }
        assertEquals(2.0, apples.quantity, 0.001)
        assertEquals(0.79, apples.unitPrice, 0.001)
        assertEquals(1.58, apples.lineTotal, 0.001)
        assertEquals(2.87, receipt.total!!, 0.001)
        assertEquals("€", receipt.currency)
        assertTrue(receipt.lines.none { it.name.uppercase().contains("SUMME") })
    }

    @Test
    fun `respects month-first dates when asked`() {
        val receipt = ReceiptParser.parse(
            """
            WHOLE FOODS MARKET
            05/06/2026
            ORGANIC BANANAS      1.29
            TOTAL                1.29
            """.trimIndent(),
            dayFirst = false,
        )

        assertEquals(epochOf("2026-05-06"), receipt.purchasedAt)
    }

    @Test
    fun `respects day-first dates when asked`() {
        val receipt = ReceiptParser.parse(
            """
            SAINSBURYS LOCAL
            05/06/2026
            MILK                 1.29
            TOTAL                1.29
            """.trimIndent(),
            dayFirst = true,
        )

        assertEquals(epochOf("2026-06-05"), receipt.purchasedAt)
    }

    @Test
    fun `falls back to null rather than inventing values`() {
        val receipt = ReceiptParser.parse("SOMETHING COMPLETELY UNREADABLE\nNO PRICES HERE")
        assertNull(receipt.total)
        assertTrue(receipt.lines.isEmpty())
    }

    @Test
    fun `sums lines when the total is missing`() {
        val receipt = ReceiptParser.parse(
            """
            CORNER SHOP
            01/02/2026
            BREAD                1.20
            BUTTER               2.30
            """.trimIndent(),
        )

        assertNotNull(receipt.total)
        assertEquals(3.50, receipt.total!!, 0.001)
    }
}
