package com.createnewera.bus;

/** Which way a port faces. The same port block, turned with the wrench. */
public enum PortMode {
    /** The network can give the device orders. */
    IN,
    /** The device publishes what it's doing to the network. */
    OUT;

    public PortMode flipped() {
        return this == IN ? OUT : IN;
    }
}
