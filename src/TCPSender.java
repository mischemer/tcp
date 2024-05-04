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
    private int largestAck;
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
            printSend(packet);

            byte[] buf = new byte[mtu+24];
            DatagramPacket receive = new DatagramPacket(buf, buf.length);
            socket.receive(receive);
            printReceive(buf);
            if(getaFlag(buf) == 1)
            {

                System.out.println("Received ack");
            }
            else
            {

                System.out.println("Didn't receive ack");
            }

            seq++;
            packet = createTCPPacket(0, 1, new byte[0], 0, 0 ,1);
            socket.send(new DatagramPacket(packet, packet.length, address, remotePort));

            printSend(packet);

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
                    
                    byte[] newBuf = new byte[te];
                    newBuf = System.arraycopy(buffer, 0, newBuf, te);
                    byte[] packet = createTCPPacket(seq, 1, newBuf, 0, 0, 0);
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
                    printSend(packet);
                    System.out.println("Sending packet");
                    seq+=te;
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
    
        System.out.println("Start fin");
        try
        {
            while(largestAck != seq)
            {
                System.out.println(largestAck + " - " + seq);
            }
            byte[] packet = createTCPPacket(seq, 1, new byte[0], 0, 1, 0);
            socket.send(new DatagramPacket(packet, packet.length, address, remotePort));
            printSend(packet);

            byte[] buf = new byte[mtu+24];
            DatagramPacket receive = new DatagramPacket(buf, buf.length);
            socket.receive(receive);
            printReceive(buf);
            System.out.println("Receive fin ak");

            seq++;
            packet = createTCPPacket(seq, 2, new byte[0], 0, 0, 1);
            socket.send(new DatagramPacket(packet, packet.length, address, remotePort));

            printSend(packet);


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
        short checksum = 0;
        int lengthFlags = length << 3;
        lengthFlags = lengthFlags | (aFlag << 0); 
        lengthFlags = lengthFlags | (fFlag << 1); 
        lengthFlags = lengthFlags | (sFlag << 2); 
	    short zero = 0;

        //create packet with 24 + data length
        ByteBuffer packet = ByteBuffer.allocate(capacity);
        //put in 4 bytes of seq 
        packet.put( ByteBuffer.allocate(4).putInt(seq).array() );
        packet.put( ByteBuffer.allocate(4).putInt(ack).array() );
        packet.put( ByteBuffer.allocate(8).putLong(System.nanoTime()).array() );
        packet.put( ByteBuffer.allocate(4).putInt(lengthFlags).array() );
        packet.put( ByteBuffer.allocate(2).putShort(zero).array() );
        packet.put( ByteBuffer.allocate(2).putShort(checksum).array() );
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
                    printReceive(buf);
                    System.out.println("Received ack");
                }
                catch(Exception e)
                {
                }
                int currAck = getAck(buf);
                if(currAck > largestAck)
                {
                    largestAck = currAck;
                }

                long rtt = getTime(buf);

                try
                {

                    sem.acquire();
                }
                catch(Exception e)
                {
                }
                while(queue.peek() != null && queue.peek().getSeq() < currAck)
                {
                    queue.pop();
                }

                if(queue.peek() != null && queue.peek().getSeq() == currAck && lastAck == currAck && currAck > lastAck)
                {
                    lastAck = currAck;
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
                        printSend(packet);
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

    public int getLength(byte[] packet)
    {
        byte[] a = parsePacket(packet, 16, 4);
        return ByteBuffer.wrap(a).getInt()>>3;
    }

    public int getfFlag(byte[] packet)
    {
        byte[] a = parsePacket(packet, 16, 4);
        return ByteBuffer.wrap(a).getInt() & (1<<1);
    }

    public int getsFlag(byte[] packet)
    {
        byte[] a = parsePacket(packet, 16, 4);
        return ByteBuffer.wrap(a).getInt() & (1<<2);
    }
    public int getaFlag(byte[] packet)
    {
        byte[] a = parsePacket(packet, 16, 4);
        return ByteBuffer.wrap(a).getInt() & (1<<0);
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
public void printReceive(byte[] packet)
    {
        StringBuilder printString = new StringBuilder();
        printString.append("rcv " + System.currentTimeMillis() + " ");
        int synFlag = getsFlag(packet);
        int ackFlag = getaFlag(packet);
        int finFlag = getfFlag(packet);
        String sString = new String();
        String aString = new String();
        String fString = new String();
        if (synFlag == 4)
        {
            sString = "S";
        }
        else
        {
            sString = "-";
        }
        if (ackFlag == 1)
        {
            aString = "A";
        }
        else
        {
            aString = "-";
        }
        
        if (finFlag == 2)
        {
            fString = "F";
        }
        else
        {
            fString = "-";
        }
        printString.append(sString + " ");
        printString.append(aString + " ");
        printString.append(fString + " ");
        int length = getLength(packet);
        if (length > 0)
        {
            printString.append("D ");
        }
        else
        {
            printString.append("- ");
        }
        printString.append(getSeq(packet) + " " + length + " " + getAck(packet));
        System.out.println(printString);
    }

    public void printSend(byte[] packet)
    {
        StringBuilder printString = new StringBuilder();
        printString.append("snd " + System.currentTimeMillis() + " ");
        int synFlag = getsFlag(packet);
        int ackFlag = getaFlag(packet);
        int finFlag = getfFlag(packet);
        String sString = new String();
        String aString = new String();
        String fString = new String();
        if (synFlag == 4)
        {
            sString = "S";
        }
        else
        {
            sString = "-";
        }
        if (ackFlag == 1)
        {
            aString = "A";
        }
        else
        {
            aString = "-";
        }

        if (finFlag == 2)
        {
            fString = "F";
        }
        else
        {
            fString = "-";
        }
        printString.append(sString + " ");
        printString.append(aString + " ");
        printString.append(fString + " ");
        int length = getLength(packet);
        if (length > 0)
        {
            printString.append("D ");
        }
        else
        {
            printString.append("- ");
        }
        printString.append(getSeq(packet) + " " + length + " " + getAck(packet));
        System.out.println(printString);
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
