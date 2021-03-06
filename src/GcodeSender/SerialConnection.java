package GcodeSender;


import jssc.*;

import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.prefs.Preferences;

import javax.swing.ButtonGroup;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

public class SerialConnection
implements SerialPortEventListener, ActionListener {
	private static String cue=">";
	private static String NL="\n";
	
	private String[] portsDetected;
	
	public static String BAUD_RATE = "57600";
	private static String [] baudsAllowed = { "300","1200","2400","4800","9600","14400","19200","28800","38400","57600","115200","125000","250000"};
	
	public SerialPort serialPort;
	public boolean portOpened=false;
	public boolean portConfirmed=false;
	public String portName;
	public boolean waitingForCue=true;
	
	// settings
	private Preferences prefs;
	
	// menus & GUIs
	JTextArea log = new JTextArea();
	JScrollPane logPane;
    private JMenuItem [] buttonPorts;
    private JMenuItem [] buttonBauds;
    
    // communications
    String line3;
    ArrayList<String> commandQueue = new ArrayList<String>();

    // Listeners which should be notified of a change to the percentage.
    private ArrayList<SerialConnectionReadyListener> listeners = new ArrayList<SerialConnectionReadyListener>();

	
	public SerialConnection(String name) {
		prefs = Preferences.userRoot().node("SerialConnection").node(name);
		
		DetectSerialPorts();

		OpenPort(GetLastPort());
	}
	
	public void finalize() {
		ClosePort();
		//super.finalize();
	}
	
	private String GetLastPort(){
		return prefs.get("last port","");
	}
	
	private void SetLastPort(String portName) {
		prefs.put("last port", portName);
	}
	
	private String GetLastBaud() {
		return prefs.get("last baud",BAUD_RATE);
	}
	
	private void SetLastBaud(String baud) {
		prefs.put("last port", baud);		
	}
	
	public void Log(String msg) {
		log.append(msg);
		log.setCaretPosition(log.getText().length());
	}
	
	public boolean ConfirmPort() {
		if(!portOpened) return false;
		
		// for gcodeSender we won't validate the connection, we'll just assume it's connected to the right machine.
/*
		if(portConfirmed) return true;
		
		String hello = "StewartPlatform v4";
		int found=line3.lastIndexOf(hello);
		if(found >= 0) {
			// get the UID reported by the robot
			String[] lines = line3.substring(found+hello.length()).split("\\r?\\n");
			if(lines.length>0) {
				try {
					robot_uid = Long.parseLong(lines[0]);
				}
				catch(NumberFormatException e) {}
			}
			
			// new robots have UID=0
			if(robot_uid==0) {
				// Try to set a new one
				GetNewRobotUID();
			}
			mainframe.setTitle("Drawbot #"+Long.toString(robot_uid));

			// load machine specific config
			LoadConfig();
			if(limit_top==0 && limit_bottom==0 && limit_left==0 && limit_right==0) {
				UpdateConfig();
			} else {
				SendConfig();
			}
			previewPane.setMachineLimits(limit_top, limit_bottom, limit_left, limit_right);
			
			// load last known paper for this machine
			GetRecentPaperSize();
			if(paper_top==0 && paper_bottom==0 && paper_left==0 && paper_right==0) {
				UpdatePaper();
			}

			portConfirmed=true;
		}
		return portConfirmed;
*/
		portConfirmed=true;
		return true;
	}
	
	@Override
	public void serialEvent(SerialPortEvent events) {
        if(events.isRXCHAR()) {
        	if(!portOpened) return;
            try {
            	int len = events.getEventValue();
				byte [] buffer = serialPort.readBytes(len);
				if( len>0 ) {
					String line2 = new String(buffer,0,len);
					Log(line2);
					line3+=line2;
					// wait for the cue to send another command
					if(line3.lastIndexOf(cue)!=-1) {
						waitingForCue=false;
						if(ConfirmPort()) {
							line3="";
							SendQueuedCommand();
						}
					}
				}
            } catch (SerialPortException e) {}
        }
	}
	
	protected void SendQueuedCommand() {
		if(!portOpened || waitingForCue) return;
		
		if(commandQueue.size()==0) {
		      notifyListeners();
		      return;
		}
		
		String command;
		try {
			command=commandQueue.remove(0);
			if(command.endsWith(";")==false) command+=";";
			//Log(command+NL);
			serialPort.writeBytes(command.getBytes());
			waitingForCue=true;
		}
		catch(IndexOutOfBoundsException e1) {}
		catch(SerialPortException e2) {}
	}
	
	public void SendCommand(String command) {
		if(!portOpened) return;
		
		commandQueue.add(command);
		if(portConfirmed) SendQueuedCommand();
	}
	
	// find all available serial ports for the settings->ports menu.
	public void DetectSerialPorts() {
        if(System.getProperty("os.name").equals("Mac OS X")){
        	portsDetected = SerialPortList.getPortNames("/dev/");
            //System.out.println("OS X");
        } else {
        	portsDetected = SerialPortList.getPortNames("COM");
            //System.out.println("Windows");
        }
	}
	
	public boolean PortExists(String portName) {
		if(portName==null || portName.equals("")) return false;

		int i;
		for(i=0;i<portsDetected.length;++i) {
			if(portName.equals(portsDetected[i])) {
				return true;
			}
		}
		
		return false;
	}
	
	public void ClosePort() {
		if(!portOpened) return;
		
	    if (serialPort != null) {
	        try {
		        // Close the port.
		        serialPort.removeEventListener();
		        serialPort.closePort();
	        } catch (SerialPortException e) {
	            // Don't care
	        }
	    }

		portOpened=false;
		portConfirmed=false;
	}
	
	// open a serial connection to a device.  We won't know it's the robot until  
	public int OpenPort(String portName) {
		if(portOpened && portName.equals(GetLastPort())) return 0;
		if(PortExists(portName) == false) return 0;
		
		ClosePort();
		
		Log("Connecting to "+portName+"..."+NL);

		Log("<font color='green'>Connecting to "+portName+"...</font>\n");
		
		// open the port
		serialPort = new SerialPort(portName);
		try {
            serialPort.openPort();// Open serial port
            serialPort.setParams(Integer.parseInt(GetLastBaud()),SerialPort.DATABITS_8,SerialPort.STOPBITS_1,SerialPort.PARITY_NONE);
            serialPort.addEventListener(this);
        } catch (SerialPortException e) {
			Log("<span style='color:red'>Port could not be configured:"+e.getMessage()+"</span>\n");
			return 3;
		}
		portOpened=true;
		SetLastPort(portName);

		return 0;
	}
	
	@Override
	public void actionPerformed(ActionEvent e) {
		Object subject = e.getSource();
		
		int i;
		for(i=0;i<portsDetected.length;++i) {
			if(subject == buttonPorts[i]) {
				OpenPort(portsDetected[i]);
				return;
			}
		}
		
		for(i=0;i<baudsAllowed.length;++i) {
			if(subject == buttonBauds[i]) {
				SetLastBaud(baudsAllowed[i]);
				return;
			}
		}
	}

    // Adds a listener that should be notified.
    public void addListener(SerialConnectionReadyListener listener) {
      listeners.add(listener);
    }

    // Notifies all the listeners
    private void notifyListeners() {
      for (SerialConnectionReadyListener listener : listeners) {
        listener.SerialConnectionReady(this);
      }
    }

	public JMenu getBaudMenu() {
		JMenu subMenu = new JMenu();
	    ButtonGroup group = new ButtonGroup();
	    buttonBauds = new JRadioButtonMenuItem[baudsAllowed.length];
	    
	    String lastBaud=GetLastBaud();
	    
		int i;
	    for(i=0;i<baudsAllowed.length;++i) {
	    	buttonBauds[i] = new JRadioButtonMenuItem(baudsAllowed[i]);
	        if(lastBaud.equals(baudsAllowed[i])) buttonBauds[i].setSelected(true);
	        buttonBauds[i].addActionListener(this);
	        group.add(buttonBauds[i]);
	        subMenu.add(buttonBauds[i]);
	    }
	    
	    return subMenu;
	}

	public JMenu getPortMenu() {
		JMenu subMenu = new JMenu();
	    ButtonGroup group = new ButtonGroup();
	    buttonPorts = new JRadioButtonMenuItem[portsDetected.length];
	    
	    String lastPort=GetLastPort();
	    
		int i;
	    for(i=0;i<portsDetected.length;++i) {
	    	buttonPorts[i] = new JRadioButtonMenuItem(portsDetected[i]);
	        if(lastPort.equals(portsDetected[i])) buttonPorts[i].setSelected(true);
	        buttonPorts[i].addActionListener(this);
	        group.add(buttonPorts[i]);
	        subMenu.add(buttonPorts[i]);
	    }
	    
	    return subMenu;
	}
	

	public Component getGUI() {
	    // the log panel
	    log.setEditable(false);
	    log.setForeground(Color.GREEN);
	    log.setBackground(Color.BLACK);
	    logPane = new JScrollPane(log);
	    
	    return logPane;
	}
}
