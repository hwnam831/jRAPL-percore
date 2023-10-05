import java.util.concurrent.locks.ReentrantLock;  
import java.io.*;  
import java.net.*; 
import java.util.*;

//import net.sourceforge.argparse4j.*;
//import net.sourceforge.argparse4j.inf.ArgumentParser;
//import net.sourceforge.argparse4j.inf.ArgumentParserException;
//import net.sourceforge.argparse4j.inf.Namespace;

class NodeStatus{
    public double powerConsumption;
    public double powerLimit;
    public double BIPS;
    public double dBdP; // dBIPS/dPower
}
/* 
class ControllerServer extends Thread{
    int numInvokers;
    ArrayList<Socket> invokerClients; // send new power limits
    ArrayList<NodeStatus> statuses;
    ServerSocket listener;
    ReentrantLock lock;
    double powerlimit;
    public static final int port = 2345;
    boolean running = true;
    public ControllerServer(double powerlimit){
        this.numInvokers = 0;
        this.lock = new ReentrantLock();
        this.invokerClients = new ArrayList<Socket>();
        this.statuses = new ArrayList<NodeStatus>();
        this.powerlimit = powerlimit;
        try{
            this.listener = new ServerSocket(port);
        } catch (Exception e){
            e.printStackTrace();
        }


    }
    public void terminate(){
        running = false;
    }
    public void run(){
        try{
            ServerSocket ss = new ServerSocket(port);
            ss.setSoTimeout(2000); // try per 2 seconds
            while(running){
                try{
                    Socket s = ss.accept();
                    DataInputStream din=new DataInputStream(s.getInputStream());  
                    DataOutputStream dout=new DataOutputStream(s.getOutputStream());  
                    
                    String str="",str2="";
                    str=din.readUTF();
                    //System.out.println("client says: "+str);
                    String [] pls = str.split(",", curpl.length);
                    
                    for (int i=0; i<curpl.length; i++){
                        double pl = Double.parseDouble(pls[i]);
                        curpl[i] = pl;
                        EnergyCheckUtils.SetPkgLimit(i, curpl[i], curpl[i]);
                    }
                    din.close();
                    dout.close();
                    s.close();
                } catch (Exception e){
                    System.err.println("Timeout. Retrying:");
                }

            
            }
            ss.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        for (int s = 0; s<num_sockets; s++){
            
            System.err.println("Reverting back to original limit");
            EnergyCheckUtils.SetPkgLimit(s, default_pl1, default_pl2);
            EnergyCheckUtils.SetRAPLTimeWindow(s, default_timewindow);
        }
    }
    
}
*/

public class ClusterController{
    public static void main(String[] args){
        for (int i=0; i<100; i++){
            try{
                Thread.sleep(100);
                Socket s = new Socket("10.10.1.1", 4545);
                //DataInputStream din=new DataInputStream(s.getInputStream());  
                //BufferedInputStream bin = new BufferedInputStream(s.getInputStream());
                BufferedReader reader = new BufferedReader(new InputStreamReader(s.getInputStream()));
                DataOutputStream dout=new DataOutputStream(s.getOutputStream());  
                String message = "55.2,13.3,4.3";
                dout.writeUTF(message);
                System.out.println(reader.readLine());
                reader.close();
                dout.close();
                s.close();
                
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }
}
