package com.ratsey.thelist.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProductLookupTest {

    // ----------------------------------------------------------------- normalize

    @Test
    fun `ean-13 passes through`() {
        assertEquals("3017620422003", Barcodes.normalize("3017620422003"))
    }

    @Test
    fun `upc-a gains the leading zero of its ean-13 form`() {
        assertEquals("0041631000564", Barcodes.normalize("041631000564"))
    }

    @Test
    fun `ean-8 passes through`() {
        assertEquals("30123456", Barcodes.normalize("30123456"))
    }

    @Test
    fun `scanner adornments are stripped`() {
        assertEquals("3017620422003", Barcodes.normalize(" 3017620422003\n"))
    }

    @Test
    fun `non-retail digit runs are refused`() {
        assertNull(Barcodes.normalize(""))
        assertNull(Barcodes.normalize("12345"))
        assertNull(Barcodes.normalize("no digits here"))
    }

    // ------------------------------------------------------------- upc-e widening

    /** The worked example from the UPC-E specification: 06543217 is 065100004327 compressed. */
    @Test
    fun `last digit 1 folds the product code out after the manufacturer`() {
        assertEquals("065100004327", Barcodes.expandUpcE("06543217"))
    }

    @Test
    fun `last digit 0 widens manufacturer then product`() {
        assertEquals("065000004327", Barcodes.expandUpcE("06543207"))
    }

    @Test
    fun `last digit 2 behaves like 1 with its own fold digit`() {
        assertEquals("065200004327", Barcodes.expandUpcE("06543227"))
    }

    @Test
    fun `last digit 3 widens to a three-digit manufacturer`() {
        assertEquals("065400000327", Barcodes.expandUpcE("06543237"))
    }

    @Test
    fun `last digit 4 widens to a four-digit manufacturer`() {
        assertEquals("065430000027", Barcodes.expandUpcE("06543247"))
    }

    @Test
    fun `last digits 5-9 widen to a five-digit manufacturer`() {
        assertEquals("065432000067", Barcodes.expandUpcE("06543267"))
    }

    @Test
    fun `only a well-formed eight-digit code widens`() {
        assertNull(Barcodes.expandUpcE("3017620422003"))
        assertNull(Barcodes.expandUpcE("96543217"))
        assertNull(Barcodes.expandUpcE("short"))
    }

    // ------------------------------------------------------------------- parsing

    @Test
    fun `a found product parses name, brand, size and first category`() {
        // Real response shape from world.openfoodfacts.org/api/v2/product/3017620422003.json
        val json = org.json.JSONObject(
            """
            {"code":"3017620422003","status":1,"status_verbose":"product found",
             "product":{"product_name":"Nutella","brands":"Ferrero, Nutella",
                        "quantity":"400 g e","categories":"Cocoa and hazelnut spreads, Spreads"}}
            """.trimIndent(),
        )
        val product = ProductLookup().parse("3017620422003", json)

        assertEquals("Nutella", product?.name)
        assertEquals("Ferrero, Nutella", product?.brands)
        assertEquals("400 g", product?.quantity)
        assertEquals("Cocoa and hazelnut spreads", product?.category)
        assertEquals("3017620422003", product?.barcode)
    }

    @Test
    fun `an unknown code is not a product`() {
        val json = org.json.JSONObject("""{"code":"0041631000564","status":0}""")
        assertNull(ProductLookup().parse("0041631000564", json))
    }

    @Test
    fun `a row without a name is treated as unknown`() {
        val json = org.json.JSONObject(
            """{"status":1,"product":{"brands":"Someone","quantity":"6 x 40 g"}}""",
        )
        assertNull(ProductLookup().parse("0041631000564", json))
    }

    @Test
    fun `empty optional fields stay null`() {
        val json = org.json.JSONObject(
            """{"status":1,"product":{"product_name":"Milk"}}""",
        )
        val product = ProductLookup().parse("3017620422003", json)
        assertEquals("Milk", product?.name)
        assertNull(product?.brands)
        assertNull(product?.quantity)
        assertNull(product?.category)
    }

    // ----------------------------------------------------------- pack size text

    @Test
    fun `the estimated-weight mark goes`() {
        assertEquals("400 g", ProductLookup().cleanQuantity("400 g e"))
    }

    @Test
    fun `a plain size is untouched`() {
        assertEquals("6 x 40 g", ProductLookup().cleanQuantity("6 x 40 g"))
    }

    @Test
    fun `a blank size is no size`() {
        assertNull(ProductLookup().cleanQuantity(""))
        assertNull(ProductLookup().cleanQuantity("   "))
    }
}
