import java.io.*;
import java.net.*;
import java.util.*;
import java.nio.*;
import java.util.concurrent.*;
import java.lang.Math;

public class TCPSender
{
    private int port;
    private String remoteIP;
    private int remotePort;
    private String fileName;
    private int mtu;
    private int sws;
    
    private int seq;
    private InetAddress address;

    private DatagramSocket socket;
    private LinkedList<Quad> queue;
    private Semaphore sem;
    private long timeOut;

    public TCPSender( int port, String remoteIP, int remotePort, String fileName, int mtu, int sws)
    {
        this.port = port;
        this.remoteIP = remoteIP;
        this.remotePort = remotePort;
        this.fileName = fileName;
        this.mtu = mtu;
        this.sws = sws;
        try{

        this.address = InetAddress.getByName(remoteIP);
        }
        catch(Exception e)
        {}

        this.queue = new LinkedList<Quad>();
        this.sem = new Semaphore(1);
        this.timeOut = 5000000000L;

    }

    public void run()
    {
        handShake();
        data();
        fin();
    }

    public void handShake()
    {
        try
        {
            this.socket = new DatagramSocket(this.port);

            byte[] packet = createTCPPacket(0, 0, new byte[0], 1, 0 ,0);
            socket.send(new DatagramPacket(packet, packet.length, address, remotePort));

            byte[] buf = new byte[mtu+24];
            DatagramPacket receive = new DatagramPacket(buf, buf.length);

            packet = createTCPPacket(0, 1, new byte[0], 0, 0 ,1);
            socket.send(new DatagramPacket(packet, packet.length, address, remotePort));

           socket.receive(receive);

           System.out.println("Finished Handshake Sender");
        }
        catch(Exception e)
        {
            e.printStackTrace();
            System.exit(-1);
        }

            
    }
    
    public void data()
    {

        Listener lThread = new Listener();
        Reader rThread = new Reader();

        lThread.start();
        rThread.start();

        FileInputStream file = null;
        try{

        file = new FileInputStream(new File(fileName));
        }
        catch(Exception e)
        {}
        byte[] buffer = new byte[mtu];

        while(true)
        {
            if(queue.size() < sws)
            {
                int te = -5;
                try{

                te = file.read(buffer);
                }
                catch(Exception e)
                {}
                if( te != -1)
                {
                    byte[] packet = createTCPPacket(seq, 1, buffer, 0, 0, 0);
                    try{
                    sem.acquire();
                    }
                    catch(Exception e)
                    {}
                    //parse packet and get time field, make into long
                    queue.push(new Quad(packet, seq, System.nanoTime() + timeOut, 0));
                    try{
                    sem.release();
                    socket.send(new DatagramPacket(packet, packet.length, address, remotePort));
                    }
                    catch(Exception e)
                    {}

                }
                else
                {
                    return;
                }
            }
        }
    }


    public void fin()
    {
    
        try
        {
            this.socket = new DatagramSocket(this.port);
            byte[] packet = createTCPPacket(seq, 1, new byte[0], 0, 1, 0);
            socket.send(new DatagramPacket(packet, packet.length, address, remotePort));

            byte[] buf = new byte[mtu+24];
            DatagramPacket receive = new DatagramPacket(buf, buf.length);

            packet = createTCPPacket(seq+1, 2, new byte[0], 0, 0, 1);
            socket.send(new DatagramPacket(packet, packet.length, address, remotePort));

            socket.receive(receive);

            System.out.println("Finished FIN protocol");

        }
        catch(Exception e)
        {
            e.printStackTrace();
            System.exit(-1);
        }
    

    }

    public byte[] changeTimeTCPPacket(byte[] packet, long time)
    {
        byte[] newPacket = new byte[packet.length];
        System.arraycopy(packet, 0, newPacket, 0, 8);
        byte[] newTime = ByteBuffer.allocate(8).putLong(time).array();
        System.arraycopy(newTime, 0, newPacket, 8, 8);
        System.arraycopy(packet, 16, newPacket, 16, packet.length - 16);

        return newPacket;
    }


    public byte[] createTCPPacket(int seq, int ack, byte[] data, int sFlag, int fFlag, int aFlag)
    {
        int length = data.length;
        int capacity = 24 + length;
        int checksum = 0;
        int lengthFlags = (length*8) + (sFlag*4) + (fFlag*2) + aFlag;

        //create packet with 24 + data length
        ByteBuffer packet = ByteBuffer.allocate(capacity);
        //put in 4 bytes of seq 
        packet.put( ByteBuffer.allocate(4).putInt(seq).array() );
        packet.put( ByteBuffer.allocate(4).putInt(ack).array() );
        packet.put( ByteBuffer.allocate(8).putLong(System.nanoTime()).array() );
        packet.put( ByteBuffer.allocate(4).putInt(lengthFlags).array() );
        packet.put( ByteBuffer.allocate(4).putInt(0).array() );
        packet.put( ByteBuffer.allocate(4).putInt(checksum).array() );
        packet.put( ByteBuffer.allocate(length).put(data).array() );

        return packet.array();


    }

    public class Listener extends Thread
    {
        private int lastAck;
        private int counter;
        private double ERTT;
        private double EDEV;


        public Listener()
        {
            lastAck = -1;
            counter = 0;
            ERTT = -1;
            EDEV = -1;
        }


        public void run()
        {
            byte[] buf = new byte[mtu+24];

            while(true)
            {
                DatagramPacket receive = new DatagramPacket(buf, buf.length);
                try
                {

                    socket.receive(receive);
                }
                catch(Exception e)
                {
                }
                int currAck = getAck(buf);

                long rtt = getTime(buf);

                try
                {

                    sem.acquire();
                }
                catch(Exception e)
                {
                }
                while(queue.peek().getSeq() < currAck)
                {
                    queue.pop();
                }

                if(queue.peek().getSeq() == currAck && lastAck == currAck)
                {
                    counter++;

                }
                else
                {
                    lastAck = currAck;
                    counter = 0;
                }

                if(counter == 4)
                {
                    byte[] packet = queue.peek().getPacket(); 

                    packet = changeTimeTCPPacket(packet, System.nanoTime());
                    try
                    {

                        socket.send(new DatagramPacket(packet, packet.length, address, remotePort));
                    }
                    catch(Exception e)
                    {
                    }


                    queue.peek().setPacket(packet);
                    queue.peek().setSeq(queue.peek().getSeq());
                    queue.peek().setTime(System.nanoTime()+timeOut);
                    queue.peek().setRe(queue.peek().getRe() + 1);

                    counter = 0;


                }
                sem.release();

                if(ERTT == -1)
                {
                    ERTT = System.nanoTime() - rtt;
                    EDEV = 0;
                    timeOut = (long)(2*ERTT);
                }
                else
                {
                    long SRTT = System.nanoTime() - rtt;
                    long SDEV = Math.abs(SRTT - (long)ERTT);
                    ERTT = 0.875*ERTT + 0.125*(double)SRTT;
                    EDEV = 0.75*EDEV + 0.25*(double)SDEV;
                    timeOut = (long)(ERTT + 4*EDEV);
                }

            }
        }


    }


    public class Reader extends Thread
    {
        public void run()
        {
            while(true)
            {


            }

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

    public class Quad
    {
        private byte[] packet;
        private int seq;
        private long time;
        private int re;

        public Quad(byte[] packet, int seq, long time,  int re)
        {
            this.packet = packet;
            this.seq = seq;
            this.time = time;
            this.re = re;
        }

        public byte[] getPacket()
        {
            return packet;
        }

        public int getSeq()
        {
            return seq;

        }
        public long getTime()
        {
            return time;
        }

        public int getRe()
        {
            return re;
        }

        public void setPacket(byte[] packet)
        {
            this.packet = packet;
        }

        public void setSeq(int seq)
        {
            this.seq = seq;
        }

        public void setTime(long time)
        {
            this.time = time;
        }

        public void setRe(int re)
        {
            this.re = re;
        }

    }



}
