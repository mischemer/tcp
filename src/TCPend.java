public class TCPend
{


    public static void main(String[] args)
    {
        int port = -1;
        String remoteIP = null;
        int remotePort = -1;
        String fileName = null;
        int mtu = -1;
        int sws = -1;

        if( args.length == 12)
        {
            for(int i = 0; i < args.length; i++)
            {
                String arg = args[i];
                if(arg.equals("-p"))
                {
                    port = Integer.parseInt(args[++i]);
                    System.out.println("p");
                }
                else if(arg.equals("-s"))
                {
                    remoteIP = args[++i];
                    System.out.println("s");
                }
                else if(arg.equals("-a"))
                {
                    remotePort = Integer.parseInt(args[++i]);
                    System.out.println("a");
                    System.out.println(args[i]);
                    
                }
                else if(arg.equals("-f"))
                {
                    fileName = args[++i];
                    System.out.println("f");
                }
                else if(arg.equals("-m"))
                {
                    mtu = Integer.parseInt(args[++i]);
                    System.out.println("m");
                }
                else if(arg.equals("-c"))
                {
                    sws = Integer.parseInt(args[++i]);
                    System.out.println("c");
                }
                else
                {
                    System.out.println("Incorrect Args");
                    return;
                }


            }

            TCPSender sender = new TCPSender(port, remoteIP, remotePort, fileName, mtu, sws);
            sender.run();

        }


        else if( args.length == 8)
        {
            for(int i = 0; i < args.length; i++)
            {
                String arg = args[i];
                if(arg.equals("-p"))
                {
                    port = Integer.parseInt(args[++i]);
                }
                else if(arg.equals("-m"))
                {
                    mtu = Integer.parseInt(args[++i]);
                }
                else if(arg.equals("-c"))
                {
                    sws = Integer.parseInt(args[++i]);
                }
                else if(arg.equals("-f"))
                {
                    fileName = args[++i];
                }
                else
                {
                    System.out.println("Incorrect Args");
                    return;
                }
                
            }
            
            TCPReceiver receiver = new TCPReceiver(port, mtu, sws, fileName);
            receiver.run();


        }

        else
        {
            System.out.println("Args");
            return;
        }



       
        





    }





}
