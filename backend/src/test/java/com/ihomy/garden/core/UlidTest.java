package com.ihomy.garden.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ULID:26 位 Crockford Base32、时间戳可复现、格式校验与幂等键归一。
 */
class UlidTest {

    @Test
    void generatedIdsAreValidAndUnique() {
        String a = Ulid.next();
        String b = Ulid.next();
        assertTrue(Ulid.isValid(a));
        assertTrue(Ulid.isValid(b));
        assertNotEquals(a, b);
        assertEquals(26, a.length());
    }

    @Test
    void timestampIsEncodedInFirstTenChars() {
        long ms = 1_700_000_000_000L;
        String id1 = Ulid.of(ms);
        String id2 = Ulid.of(ms);
        assertEquals(id1.substring(0, 10), id2.substring(0, 10));
        assertTrue(Ulid.isValid(id1));
    }

    @Test
    void rejectsBadFormats() {
        assertFalse(Ulid.isValid(null));
        assertFalse(Ulid.isValid(""));
        assertFalse(Ulid.isValid("short"));
        assertFalse(Ulid.isValid("!!!!!!!!!!!!!!!!!!!!!!!!!!"));
        // U 不在 Crockford 字母表(26 位)
        assertFalse(Ulid.isValid("01ARZ3NDEKTSV4UUUUUUUUUUU"));
    }

    @Test
    void orNextPassesThroughValidKeys() {
        String id = Ulid.next();
        assertEquals(id, Ulid.orNext(id));
        assertNotEquals(id, Ulid.orNext(null));
        assertNotEquals(id, Ulid.orNext("bogus"));
    }
}
