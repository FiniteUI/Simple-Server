package assignment3GUI;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.table.DefaultTableModel;

//this is the class for the UDP listening thread
class UDPListener extends Thread {
    
    private byte[] buffer = new byte[128];  //the buffer to pass to the Datagrampacket
    private byte[] data;                    //the data recieved as bytes
    private DatagramSocket ds;              //the datagram socket to recieve the datagram packet
    private String packetString;            //the data recieved as a string
    private final DatagramPacket dp;        //the datagrampacket to get the data
    private final VehicleList vList;        //the list of vehicles
 
    //constructor, open the socket and datagram packet, accept the synchronized list
    public UDPListener(VehicleList vList) {
        try {
            ds = new DatagramSocket(9099);
        }//end try
        catch(Exception e) {
            e.printStackTrace();
        }//end cacth
        this.vList = vList;
        dp = new DatagramPacket(buffer, 128);
    }//end constructor
    
    //this is where the magic happens, get data, check checksum, send it to the vehicle list to interperet
    public void run() {
        while(true) {
            try {
                ds.receive(dp);
                data = dp.getData();
                packetString = new String(dp.getData(), 0, dp.getLength());
                writeUDP("Recieved UDP message: " + packetString);
                if (checksum(data)) {
                    vList.newData(packetString);
                }//end if
                else
                    write("Invalid checksum in UDP message: " + packetString.substring(0, packetString.length() - 1) + ", "  + Thread.currentThread());
            }//end try
            catch(Exception e) {
                e.printStackTrace();
            }//end catch
        }//end while
    }//end run
    
    //returns if the checksum is valid or not
    private boolean checksum(byte[] b) {
        int checksumOffset = 0;
        byte check = b[1];
        for (int i = 2; i < b.length; i++) {
            if (Byte.toUnsignedInt(b[i]) == 42) {
                checksumOffset = i;
                break;
            }//end if
            check = (byte) (check ^ b[i]);
        }//end for
        
        //for converting the ascii hex binary to ascii hex to decimal (what a pain)
        char x = (char) b[checksumOffset + 1];
        char y = (char) b[checksumOffset + 2];
        String hexStr = "";
        hexStr += x;
        hexStr += y;
        check = (byte) (check ^ Integer.parseInt(hexStr, 16));
        if (check == 0)
            return true;
        return false;
    }//end checksum
    
    private String consoleBuffer = "";      //a way of getting messages from this thread to the gui
    
    //adds text to the console buffer
    private void write(String s) {
        consoleBuffer += s + "\n";
    }//end write
    
    private String consoleBufferUDP = "";   //a way of getting the optionally show UDP messages to the gui
 
    //add udp messages to udp buffer
    private void writeUDP(String s) {
        consoleBufferUDP += s;
    }//end write
    
    //for the gui, get the messages
    public String getConsoleBuffer() {
        String temp = consoleBuffer;
        consoleBuffer = "";
        return temp;
    }//end getConsoleBuffer
    
    //for the gui, get the udp message
    public String getConsoleBufferUDP() {
        String temp = consoleBufferUDP;
        consoleBufferUDP = "";
        return temp;
    }//end getConsoleBuffer
    
    //we don't always want the udp messages, so don't let the buffer fill up forever, we don't need old messages
    //clears the udp buffer
    public void clearConsoleBufferUDP() {
        consoleBufferUDP = "";
    }//end clearConsoleBufferUDP
    
}//end UDPListener

//--------------------------------------------------------------------------------------------

//this class contains the synchronized vehicle list 
class VehicleList {
    private ArrayList<vehicle> vehicles;        //the vehicles
    private ArrayList<Long> updateTimes;        //the times each vehicle was last updated (same postion as vehicle)
    private ExecutorService sweeper;            //a thread to clear out timed out vehicles
    
    //constructor, make the arraylists, start the sweeper thread
    public VehicleList() {
        vehicles = new ArrayList<>();
        updateTimes = new ArrayList<>();
        sweeper = Executors.newSingleThreadExecutor();
        sweeper.execute(timer);
    }//end constructor
    
    //this is the sweeper thread, every now and then clear out timed out vehicles
    Runnable timer = new Runnable() {
      public void run() {
          while(true) {
              try {
                Thread.sleep(5000);
              }//end try
              catch (Exception e) {
                  e.printStackTrace();
              }//end catch
              for(int i = 0; i < updateTimes.size(); i++) {
                  if (Instant.now().toEpochMilli() - updateTimes.get(i) > 30000) {
                      deleteVehicle(i);
                  }//end if
              }//end for
          }//end while
      }//end run  
    };
    
    //takes new data and adds it to the list 
    public void newData(String data) {
        
        //parse data
        String[] fields = data.split(",");
        String[] idAndChecksum = fields[fields.length - 1].split("\\*");
        int id = Integer.parseInt(idAndChecksum[0]);
        //fields[0] = $GPRMC
        String time = fields[1].substring(0,2) + ":" + fields[1].substring(2,4) + ":" + fields[1].substring(4,6);
        //fields[2] = status
        Double lat = Double.parseDouble(fields[3]);
        if (fields[4].equalsIgnoreCase("S"))
            lat = 0 - lat;
        Double lon = Double.parseDouble(fields[5]);
        if (fields[6].equalsIgnoreCase("W"))
            lon = 0 - lon;
        Double speed = Double.parseDouble(fields[7]) * 1.15078;
        Double heading = Double.parseDouble(fields[8]);
        
        vehicle v = new vehicle(id, fields[2], lat, lon, speed, heading, time);
        int location = searchID(id);
        insertVehicle(v, location);
    }//end newVehicle
    
    //puts a vehicle in the list, either in a position or at the end
    public synchronized void insertVehicle(vehicle v, int loc) {
        if (loc == -1) {
            vehicles.add(v);
            updateTimes.add(Instant.now().toEpochMilli());
        }//end if
        else {
            vehicles.set(loc, v);
            updateTimes.set(loc, Instant.now().toEpochMilli());
        }//end else
    }//end insertVehicle
    
    //removes a vehicle from the list
    public synchronized void deleteVehicle(int loc) {
        write("Removing vehicle " + vehicles.get(loc).getID() + " due to timeout " + Thread.currentThread());
        vehicles.remove(loc);
        updateTimes.remove(loc);
    }//end deleteVehicle
    
    //returns the location of a specified vehicle in the list, or -1 if not found
    private synchronized int searchID(int id) {
        int loc = -1;
        for (int i = 0; i < vehicles.size(); i++) {
            if (vehicles.get(i).getID() == id) {
                loc = i;
                break;
            }//end if
        }//end for
        return loc;
    }//end searchID
    
    //get all the data for json response
    public synchronized String getAllVehicleData() {
        String s = "";
        for (int i = 0; i < vehicles.size(); i++)
            s += vehicles.get(i).getVehicleData() + ",";
        if (s.length() != 0)
            return s.substring(0, s.length()-1);
        else
            return s;
    }//end getAllVehicleData
    
    //get data on active vehicles for json response
    public synchronized String getActiveVehicleData() {
        String s = "";
        for (int i = 0; i < vehicles.size(); i++) {
            if (vehicles.get(i).getStatus().equalsIgnoreCase("A"))
                s += vehicles.get(i).getVehicleData() + ",";
        }//end for
        if (s.length() != 0)
            return s.substring(0, s.length()-1);
        else
            return s;
    }//end getActiveVehicleData
    
    public int getSize() {
        return vehicles.size();
    }//end getSize
    
    private String consoleBuffer = "";  //same as the one in the other class, for getting information from here to gui
    
    //add data to console buffer
    private void write(String s) {
        consoleBuffer += s + "\n";
    }//end write
   
    //returns and then clears the console buffer
    public String getConsoleBuffer() {
        String temp = consoleBuffer;
        consoleBuffer = "";
        return temp;
    }//end getConsoleBuffer
}//end VehicleList

//----------------------------------------------------------------------------------------------------

//this is a class for the vehicle data type, maybe unneccesary, but it's more fun this way
class vehicle {
    private int id;             //vehicle id
    private String status;      //vehicle status
    private double lat;         //latitude
    private double lon;         //longitude
    private double speed;       //speed mph
    private double heading;     //vehicle heading
    private String time;        //time of last update
    
    //constructor, assign values
    public vehicle(int id, String status, double lat, double lon, double speed, double heading, String time) {
        this.id = id;
        this.status = status;
        this.lat = lat;
        this.lon = lon;
        this.speed = speed;
        this.heading = heading;
        this.time = time;
    }//end constructor
    
    //returns the vehicle id
    public int getID() {
        return id;
    }//end getID
    
    //returns the vehicle status
    public String getStatus() {
        return status;
    }//end getStatus
    
    //returns the vehicle info prepped as xml
    public String getVehicleData() {
        String s = "{";
        s += "\"ident\":\"" + id + "\"" + ", ";
        s += "\"status\":\"" + status + "\"" + ", ";
        s += "\"latitude\":\"" + lat + "\"" + ", ";
        s += "\"longitude\":\"" + lon + "\"" + ", ";
        s += "\"speed\":\"" + speed + "\"" + ", ";
        s += "\"heading\":\"" + heading + "\"" + ", ";
        s += "\"time\":\"" + time + "\"";
        s += "}";
        return s;
    }//end getVehicleData
}//end vehicle

//------------------------------------------------------------------------------

//this class is the listener for the tcp connections
class TCPListener extends Thread {
    private ArrayList<TCPDealer> dealers = new ArrayList<>();   //all the threads created for connections
    private ServerSocket listener;                              //the main listener socket
    private VehicleList vList;                                  //the synchronized list of vehicles
    private ExecutorService connectionChecker;                  //a thread to clear out closed connections
    
    //this is for the connectionChecker executor, clears out closed connections
    Runnable openChecker = new Runnable() {
      public void run() {
          try {
              while(true) {
                Thread.sleep(5000);
                for(int i = 0; i < dealers.size(); i++) {
                    if (!(dealers.get(i).getConnectionState())) {
                        write("Connection to " + dealers.get(i) + " closed.");
                        dealers.remove(i);
                    }//end if
                }//end for
              }//end while
          }//end try
          catch (Exception e) {
              e.printStackTrace();
          }//end catch
      }//end run  
    };
    
    //constructor, accept the vehicle list, start the connection checker thread
    public TCPListener(VehicleList vList) {
        this.vList = vList;
        connectionChecker = Executors.newSingleThreadExecutor();
        connectionChecker.execute(openChecker);
    }//end constructor
    
    //this is where the magic happens. listens for connections and makes new threads to deal with them
    public void run() {
        try {
            listener = new ServerSocket(9099);
            while(true) {
                TCPDealer d = new TCPDealer(listener.accept(), vList, Instant.now().toEpochMilli());
                dealers.add(d);
                d.start();
            }//end while
        }//end try
        catch (Exception e) {
            e.printStackTrace();
        }//end catch
    }//end run
    
    //returns the total number of connections, for gui
    public int getNumberOfConnections() {
        return dealers.size();
    }//end getNumberOfConnections
    
    private String consoleBuffer = "";      //same as before, a way of getting data from the layered threads and objects the gui thread
    
    //add data to the buffer
    private void write(String s) {
        consoleBuffer += s + "\n";
    }//end write

    //return and clear the buffer, also the buffers of subordinate threads to deal with connections
    public String getConsoleBuffer() {
        String temp = consoleBuffer;
        for (int i = 0; i < dealers.size(); i++) {
            temp += dealers.get(i).getConsoleBuffer();
        }//end for
        consoleBuffer = "";
        return temp;
    }//end getConsoleBuffer
}//end TCPListener

//------------------------------------------------------------------------------

//this class deals with the accepted TCP connections
class TCPDealer extends Thread {
    private Socket s;                                                       //the socket to handle the connection
    private String validXML1 = "<AVL><vehicles>all</vehicles></AVL>";       //proper xml request for all vehicles
    private String validXML2 = "<AVL><vehicles>active</vehicles></AVL>";    //proper xml request for active vehicles
    private VehicleList vList;                                              //the synchronized vehicle list
    private long lastUpdate;                                                //the time of the last update to this monitor
    
    //construcot, accept the vehicle list, socket, and first update time
    public TCPDealer(Socket s, VehicleList vList, long lastUpdate) {
        this.s = s;
        this.vList = vList;
        this.lastUpdate = lastUpdate;
    }//end constructor
    
    //try to read from the connection to see if it's still open
    //if it is, you just messed up the xml it's sending you, account for that in the xml dealer
    public boolean getConnectionState() {
        try {
            if (s.getInputStream().read() == -1)
                return false;
        }//end try
        catch (Exception e) {
            e.printStackTrace();
            return false;
        }//end catch
        return true;
    }//end getConnectionState
    
    //this is where the magic happens, accept xml, make sure update at least every 30 seconds, write out the json responses
    public void run() {
        write("New connection made: " + Thread.currentThread());
        try {
            BufferedReader dataIn = new BufferedReader(new InputStreamReader(s.getInputStream()));
            DataOutputStream dataOut = new DataOutputStream(s.getOutputStream());
            
            while ((Instant.now().toEpochMilli() - lastUpdate) < 30000) {
                if (dataIn.ready()) {
                    
                    lastUpdate = Instant.now().toEpochMilli();
                    String xml = dataIn.readLine();
                    
                    //fix xml broken by my connection checker
                    if (!xml.startsWith("<"))
                        xml = "<" + xml;
                    
                    write("New XML request recieved: " + Thread.currentThread() + ", " + xml);
                    
                    //okay, I know this is a cheap and lame xml parser, but using SAX or DOM or anything else I could find
                    //was a massive pain, and there's only two valid messages anyway, so this is probably more efficient
                    if (xml.equalsIgnoreCase(validXML1)) {
                        dataOut.writeBytes(constructJSON(true));
                    }//end if
                    else if (xml.equalsIgnoreCase(validXML2)){
                        dataOut.writeBytes(constructJSON(false));
                    }//end else if
                    else {
                        write("Invalid XML: " + xml + ", "  + Thread.currentThread() + ", replying: {\"status\":\"invalid\",\"vehicles\":[]}");
                        dataOut.writeBytes("{\"status\":\"invalid\",\"vehicles\":[]}\n");
                    }//end else
                }//end if
            }//end while
            dataIn.close();
            dataOut.close();
            write("Closing connection due to timeout: " + Thread.currentThread());
            s.close();
        }//end try
        catch (Exception e) {
            e.printStackTrace();
        }//end catch
    }//end run
    
    //make the json response
    private String constructJSON(boolean all) {
        String json = "";
        if (all) {
            json += "{\"status\":\"all\",\"vehicles\":[";
            json += vList.getAllVehicleData();
        }//end if
        else {
            json += "{\"status\":\"active\",\"vehicles\":[";
            json += vList.getActiveVehicleData();
        }//end else
        json += "]}\n";
        write("Replying: " + Thread.currentThread() + " " + json.substring(0, json.length() - 1));
        return json;
    }//end constructJSON
    
    private String consoleBuffer = "";      //same as before, a way of getting data from here to the gui
    
    //add data to the buffer
    private void write(String s) {
        consoleBuffer += s + "\n";
    }//end write
    
    //return and clear the buffer
    public String getConsoleBuffer() {
        String temp = consoleBuffer;
        consoleBuffer = "";
        return temp;
    }//end getConsoleBuffer
}//end TCPDealer

//------------------------------------------------------------------------------

//this class is the gui
public class Assignment3ServerGUI extends javax.swing.JFrame {
    private static ExecutorService guiUpdater = Executors.newSingleThreadExecutor();    //a thread to update the gui
    private static VehicleList vList;                                                   //the synchronized vehicle list
    private static UDPListener u;                                                       //the udp listener
    private static TCPListener t;                                                       //the tcp listener
    private static boolean showUDP = false;                                             //show udp messages in GUI?
    private static DefaultTableModel vehicleTableModel = new DefaultTableModel(
            new Object [][] {
            },
            new String [] {
                "ID", "Status", "Lat", "Lon", "Speed", "Heading", "Time"
            });                                                                         //table model for showing vehicles
    
    //this is for the thread that will update the gui with new information
    static Runnable updateGUI = new Runnable() {
        String[] rawData;                   //the data of all the vehicles
        String[][] tableData;               //the final data for the table
        String[] rowData = new String[7];   //the data of one row
        int vSize = 0;                      //number of vehicles
        boolean updated = true;             //does the table need to be updated?

        //this is where the magic happens, write out all the buffers, update the table
        public void run() {
            while(true) {
                if (showUDP)
                    writeToGUIConsole(u.getConsoleBufferUDP());
                else
                    u.clearConsoleBufferUDP();
                writeToGUIConsole(u.getConsoleBuffer());
                writeToGUIConsole(vList.getConsoleBuffer());
                writeToGUIConsole(t.getConsoleBuffer());
                CurrentConnectionCountTextField.setText(Integer.toString(t.getNumberOfConnections()));
                vSize = vList.getSize();
                CurrentVehicleCountTextField.setText(Integer.toString(vSize));
                
                updated = vehicleTableModel.getRowCount() != vSize;
                
                if (updated) {
                    rawData = vList.getAllVehicleData().split(",");
                    tableData = new String[vSize][7];
                    for (int i = 0; i < vSize; i++) {
                        for(int j = 0; j < 7; j++) {
                            if (j != 6)
                                rowData[j] = rawData[7 * i + j].substring(rawData[7 * i + j].indexOf(':') + 2, rawData[7 * i + j].length() - 1);
                            else
                                rowData[j] = rawData[7 * i + j].substring(rawData[7 * i + j].indexOf(':') + 2, rawData[7 * i + j].length() - 2);
                        }//end for
                        tableData[i] = rowData;
                        rowData = new String[7];
                    }//end for
                    vehicleTableModel.setRowCount(0);
                    for(int i = 0; i < tableData.length; i++) {
                        vehicleTableModel.addRow(tableData[i]);
                    }//end for
                    VehicleTable.setModel(vehicleTableModel);
                    updated = false;
                }//end if
            }//end while
        }//end run
    };//end runnable
    
    //the main method for this whole assignment, make the list, start the listeners, run the gui
    public static void main(String[] args) {
        //start the server
        vList = new VehicleList();
        u = new UDPListener(vList);
        u.start();
        t = new TCPListener(vList);
        t.start();
        
        //run the gui
        
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new Assignment3ServerGUI().setVisible(true);
            }
        });
    }//end main
    
    //write out data to the text area on the gui
    public static void writeToGUIConsole(String s) {
        ConsoleTextArea.append(s);
        ConsoleScrollPane.getVerticalScrollBar().setValue(ConsoleScrollPane.getVerticalScrollBar().getMaximum());
    }//end writeToGUICOnsole

    //constructor, mostly written by netbeans, initialize, start the gui updater thread
    public Assignment3ServerGUI() {
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException ex) {
            java.util.logging.Logger.getLogger(Assignment3ServerGUI.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            java.util.logging.Logger.getLogger(Assignment3ServerGUI.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            java.util.logging.Logger.getLogger(Assignment3ServerGUI.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(Assignment3ServerGUI.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        initComponents();
        guiUpdater.execute(updateGUI);
    }

    //build the gui
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        CurrentVehicleCountLabel = new javax.swing.JLabel();
        CurrentConnectionCountLabel = new javax.swing.JLabel();
        CurrentVehicleCountTextField = new javax.swing.JTextField();
        CurrentConnectionCountTextField = new javax.swing.JTextField();
        VehicleTableScrollPane = new javax.swing.JScrollPane();
        VehicleTable = new javax.swing.JTable();
        ConsoleScrollPane = new javax.swing.JScrollPane();
        ConsoleTextArea = new javax.swing.JTextArea();
        ShowUDPCheckBox = new javax.swing.JCheckBox();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        CurrentVehicleCountLabel.setText("Current Vehicle Count: ");

        CurrentConnectionCountLabel.setText("Current Connection Count: ");

        CurrentVehicleCountTextField.setEditable(false);
        CurrentVehicleCountTextField.setText("jTextField1");

        CurrentConnectionCountTextField.setEditable(false);
        CurrentConnectionCountTextField.setText("jTextField1");

        VehicleTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null, null, null, null},
                {null, null, null, null, null, null, null},
                {null, null, null, null, null, null, null},
                {null, null, null, null, null, null, null}
            },
            new String [] {
                "ID", "Status", "Lat", "Lon", "Speed", "Heading", "Time"
            }
        ));
        VehicleTableScrollPane.setViewportView(VehicleTable);

        ConsoleTextArea.setEditable(false);
        ConsoleTextArea.setColumns(20);
        ConsoleTextArea.setRows(5);
        ConsoleScrollPane.setViewportView(ConsoleTextArea);

        ShowUDPCheckBox.setText("Show UDP Messages");
        ShowUDPCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ShowUDPCheckBoxActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(64, 64, 64)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(CurrentVehicleCountLabel)
                            .addComponent(CurrentConnectionCountLabel))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(CurrentConnectionCountTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(CurrentVehicleCountTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 565, Short.MAX_VALUE)
                                .addComponent(ShowUDPCheckBox)
                                .addGap(137, 137, 137))))
                    .addGroup(layout.createSequentialGroup()
                        .addGap(36, 36, 36)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(VehicleTableScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 1057, Short.MAX_VALUE)
                            .addComponent(ConsoleScrollPane))))
                .addContainerGap(45, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(27, 27, 27)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(CurrentVehicleCountLabel)
                    .addComponent(CurrentVehicleCountTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(ShowUDPCheckBox))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(CurrentConnectionCountLabel)
                    .addComponent(CurrentConnectionCountTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(29, 29, 29)
                .addComponent(VehicleTableScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 123, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(ConsoleScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 394, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(32, Short.MAX_VALUE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void ShowUDPCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ShowUDPCheckBoxActionPerformed
        showUDP = !showUDP;
    }//GEN-LAST:event_ShowUDPCheckBoxActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private static javax.swing.JScrollPane ConsoleScrollPane;
    private static javax.swing.JTextArea ConsoleTextArea;
    private javax.swing.JLabel CurrentConnectionCountLabel;
    private static javax.swing.JTextField CurrentConnectionCountTextField;
    private javax.swing.JLabel CurrentVehicleCountLabel;
    private static javax.swing.JTextField CurrentVehicleCountTextField;
    private javax.swing.JCheckBox ShowUDPCheckBox;
    private static javax.swing.JTable VehicleTable;
    private javax.swing.JScrollPane VehicleTableScrollPane;
    // End of variables declaration//GEN-END:variables
}
