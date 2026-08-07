package manuel.rpckvstore.Packet;

import java.io.Serializable;

public class Promise implements Serializable {

    private final boolean promised;
    private final Float acceptedBallot;   
    private final Packet acceptedValue; 

    private Promise(boolean promised, Float acceptedBallot, Packet acceptedValue) {
        this.promised = promised;
        this.acceptedBallot = acceptedBallot;
        this.acceptedValue = acceptedValue;
    }

    public static Promise rejected() {
        return new Promise(false, null, null);
    }

    public static Promise of(Float acceptedBallot, Packet acceptedValue) {
        return new Promise(true, acceptedBallot, acceptedValue);
    }

    public boolean isPromised() {
        return promised;
    }

    public boolean hasAcceptedValue() {
        return acceptedValue != null;
    }

    public Float getAcceptedBallot() {
        return acceptedBallot;
    }

    public Packet getAcceptedValue() {
        return acceptedValue;
    }
}
