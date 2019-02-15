package org.yamcs.tctm.ccsds;

/**
 * Transfer frame is an interface covering the three CCSDS tranfer frames types:
 * <ul>
 * <li>TM (CCSDS 132.0-B-2)</li>
 * <li>AOS (CCSDS 732.0-B-3)</li>
 * <li>UNIFIED ( 732.1-B-1)</li>
 * </ul>
 * <p>
 * <p>
 * All three of them have the following structure:
 * <ul>
 * <li>Primary Header</li>
 * <li>Insert Zone/Secondary Header</li>
 * <li>Data Field</li>
 * <li>Operational Control Field (OCF)</li>
 * <li>Error Control Field</li>
 * </ul>
 * <p>
 * Note that for USLP, the data field has also a header.
 * <p>
 * In the dataStart, and dataLength properties below, only the real data (i.e. excluding the data field header) is
 * considered.
 * <p>
 * 
 * The idea is that the {@link VirtualChannelHandler} that deals with the data, has to have all the information on how
 * to interpret the data part.
 * <p>
 * For the purpose of packet extraction each frame has defined three offsets:<br>
 * dataStart &lt;= firstSduStart &lt; dataEnd
 * <p>
 * <p>
 * firstSduStart refers to the first SDU that starts in this frame.
 * <p>
 * The data in between dataStart and firstSduStart is part of a previous packet and will be used only if there is no
 * discontinuity in the frame sequence count.
 * 
 * @author nm
 *
 */
public interface TransferFrame {
    /**
     * 
     * @return virtual channel frame count
     */
    long getVcFrameSeq();

    /**
     * 
     * @return master channel id
     */
    int getSpacecraftId();

    /**
     * 
     * @return virtual channel id
     */
    int getVirtualChannelId();


    /**
     * Returns the number of frames lost from the previous sequence to this one.
     * If no frame has been lost (i.e. if prevFrameSeq and getFrameSeq() are in order) then return 0.
     * 
     * -1 means that a number of lost frames could not be determined - if there is some indication that the stream has
     * been reset
     * 
     * @param prevFrameSeq
     * @return
     */
    int lostFramesCount(long prevFrameSeq);

    byte[] getData();

    /**
     * Where in the byte array returned by {@link #getData} starts the data.
     * 
     * @return
     */
    int getDataStart();

    /**
     * Where in the byte array returned by {@link #getData} starts the first packet (assuming this is a frame containing
     * packets).
     * 
     * Returns -1 if there is no packet starting in this frame.
     * 
     * @return the offset of the first packet that starts in this frame or -1 if no packet starts in this frame
     */
    int getFirstHeaderPointer();

    /**
     * The offset in the buffer where the data ends.
     * 
     * @return data end
     */
    int getDataEnd();

    /**
     * 
     * @return true if this frame contains only idle (fill) data.
     */
    boolean containsOnlyIdleData();

    /**
     * 
     * @return true if this frame has an Operation Control Field set.
     */
    boolean hasOcf();

    /**
     * Get the 4 bytes operational control field. This has a meaningful value only if the {@link #hasOcf} returns
     * true.
     * 
     * @return the Operational Control Field.
     */
    int getOcf();

}
