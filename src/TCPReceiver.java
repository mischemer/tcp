import java.io.*;
import java.net.*;
import java.util.*;
import java.nio.*;
import java.util.concurrent.*;

public class TCPReceiver
{
    private int port;
    private int mtu;
    private int sws;
    private String fileName;

    private DatagramSocket listenOnSocket;
    private ArrayList<byte[]> queue;
    private int portToSend;
    private InetAddress addressToSend;

    public TCPReceiver(int port, int mtu, int sws, String fileName)
    {

        this.port = port;
        this.mtu = mtu;
        this.sws = sws;
        this.fileName = fileName;

        this.queue = new ArrayList<byte[]>();
       

    }

    public void run()
    {
        handShake();
        data();
    }

    public void data()
    {
        int ack = Integer.MAX_VALUE;
        byte[] buf = new byte[mtu + 24];

        while(true)
        {
            DatagramPacket receive = new DatagramPacket(buf, buf.length);
            try{
            listenOnSocket.receive(receive);
            }
            catch(Exception e)
            {}

            int currSeq = getSeq(buf);
            int i = 0;
            for(i = 0; i < queue.size(); i++)
            {
                if(currSeq < getSeq(queue.get(i)))
                {
                    break;
                }
            }
            queue.add(i, buf);

            for(int j = 0; j < queue.size(); j++)
            {
                int seq = getSeq(queue.get(i));
                int length = getLengthFlags(queue.get(i))/8;

                if(ack < seq)
                {
                    break;
                }
                queue.remove(i);
                ack = seq + length + 1;

            }

        }

    }

    public void handShake()
    {
        try
        {

            this.listenOnSocket = new DatagramSocket(port);

            byte[] buf = new byte[mtu + 24];

            DatagramPacket receive = new DatagramPacket(buf, buf.length);
            listenOnSocket.receive(receive);


            portToSend = receive.getPort();
            addressToSend = receive.getAddress();

            byte[] packet = createTCPPacket(0, 1, new byte[0], 1, 0, 1);
            listenOnSocket.send(new DatagramPacket(packet, packet.length, addressToSend, portToSend));
            System.out.println("Finished Handshake");
        }

        catch(Exception e)
        {
            e.printStackTrace();
        }


    }

    public int getSeq(byte[] packet)
    {
        byte[] a = parsePacket(packet, 0, 4);
        return ByteBuffer.wrap(a).getInt();
    }

    public int getAck(byte[] packet)
    {
        byte[] a = parsePacket(packet, 4, 4);
        return ByteBuffer.wrap(a).getInt();
    }

    public long getTime(byte[] packet)
    {
        byte[] a = parsePacket(packet, 8, 8);
        return ByteBuffer.wrap(a).getLong();
    }

    public int getLengthFlags(byte[] packet)
    {
        byte[] a = parsePacket(packet, 16, 4);
        return ByteBuffer.wrap(a).getInt();
    }

    public int getCheck(byte[] packet)
    {
        byte[] a = parsePacket(packet, 22, 2);
        return ByteBuffer.wrap(a).getInt();
    }


    public byte[] parsePacket(byte[] packet, int index, int length)
    {
        byte[] copy = new byte[length];
        System.arraycopy(packet, index, copy, 0, length);
        return copy;
    }


    public byte[] createTCPPacket(int seq, int ack, byte[] data, int sFlag, int fFlag, int aFlag)
    {
        int length = data.length;
        int capacity = 24 + length;
        int checksum = 0;
        int lengthFlags = (length * 8) + (sFlag * 4) + (fFlag * 2) + aFlag;
        
        //create packet with 24 + data length
        ByteBuffer packet = ByteBuffer.allocate(capacity);
        //put in 4 bytes of seq
        packet.put( ByteBuffer.allocate(4).putInt(seq).array() );
        packet.put( ByteBuffer.allocate(4).putInt(ack).array() );
        packet.put( ByteBuffer.allocate(8).putLong(System.nanoTime()).array());
        packet.put( ByteBuffer.allocate(4).putInt(lengthFlags).array() );
        packet.put( ByteBuffer.allocate(2).putInt(0).array() );
        packet.put( ByteBuffer.allocate(2).putInt(checksum).array() );
        packet.put( ByteBuffer.allocate(length).put(data).array() );

        return packet.array();

    }

    
}
