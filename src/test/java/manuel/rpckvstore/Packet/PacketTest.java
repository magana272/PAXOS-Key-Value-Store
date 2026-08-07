package manuel.rpckvstore.Packet;

import org.json.JSONException;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PacketTest {

    @Test
    void parsesPutRequest() {
        Packet p = new Packet("{\"TYPE\":\"PUT\",\"KEY\":\"k\",\"VALUE\":\"v\"}");

        assertEquals(TYPE.PUT, p.getType());
        assertEquals("k", p.getKey());
        assertEquals("v", p.getValue());
    }

    @Test
    void parsesGetRequest() {
        Packet p = new Packet("{\"TYPE\":\"GET\",\"KEY\":\"g\"}");

        assertEquals(TYPE.GET, p.getType());
        assertEquals("g", p.getKey());
        assertNull(p.getValue());
    }

    @Test
    void parsesDeleteRequest() {
        Packet p = new Packet("{\"TYPE\":\"DELETE\",\"KEY\":\"d\"}");

        assertEquals(TYPE.DELETE, p.getType());
        assertEquals("d", p.getKey());
    }

    @Test
    void malformedRequestThrows() {
        assertThrows(JSONException.class, () -> new Packet("not json"));
    }

    @Test
    void unknownTypeThrows() {
        assertThrows(JSONException.class,
                () -> new Packet("{\"TYPE\":\"WIPE\",\"KEY\":\"k\"}"));
    }

    @Test
    void addressConstructorStoresAddressAndPort() throws Exception {
        InetAddress addr = InetAddress.getByName("127.0.0.1");
        Packet p = new Packet("{\"TYPE\":\"GET\",\"KEY\":\"k\"}", addr, 5000);

        assertEquals(addr, p.getAddress());
        assertEquals(5000, p.getPort());
    }

    @Test
    void gettersAndSettersRoundTrip() {
        Packet p = new Packet("{\"TYPE\":\"GET\",\"KEY\":\"k\"}");

        p.setKey("k2");
        p.setValue("v2");
        p.setrequest("req");
        p.setType(TYPE.PUT);
        p.setResponse("Accepted");

        assertEquals("k2", p.getKey());
        assertEquals("v2", p.getValue());
        assertEquals("req", p.getrequest());
        assertEquals(TYPE.PUT, p.getType());
        assertEquals("Accepted", p.getResponse());
    }

    @Test
    void responseDefaultsNull() {
        assertNull(new Packet("{\"TYPE\":\"GET\",\"KEY\":\"k\"}").getResponse());
    }
}