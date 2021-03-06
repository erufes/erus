package erus.android;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import android.util.Log;

public class MessageAssembler
{
	public static final String TAG = "MessageAssembler";
	
	private static final int BUFFER_SIZE = 2000000;
	
	private Connection connection;
		
	private byte[] connectionBuffer;
	private int bufferEnd;
	
	private List<byte[]> msgBuffer;
	
	private int[] msgSizeTable;
	
	private boolean disconnected;
	
	MessageAssembler(Connection connection)
	{
		this.connection = connection;
		
		msgBuffer = new ArrayList<byte[]>();
		connectionBuffer = new byte[BUFFER_SIZE];
		bufferEnd = 0;
		
		disconnected = false;
		
		initMsgSizeTable();
	}
	
	private void initMsgSizeTable()
	{
		msgSizeTable = new int[255];
		
		for(int i=0; i<255; i++)
		{
			msgSizeTable[i] = 0;
		}
		
		msgSizeTable[Protocol.MOTOR_FD] = 3;
		msgSizeTable[Protocol.MOTOR_FE] = 3;
		msgSizeTable[Protocol.MOTOR_TD] = 3;
		msgSizeTable[Protocol.MOTOR_TE] = 3;
		msgSizeTable[Protocol.MOTOR_UNICO] = 5;
		msgSizeTable[Protocol.ULTRASOUND] = 7;
		msgSizeTable[Protocol.CLAW_HORIZONTAL] = 3;
		msgSizeTable[Protocol.CLAW_VERTICAL] = 3;
		msgSizeTable[Protocol.SERVO] = 2;
		msgSizeTable[Protocol.ENCODER] = 9;
		msgSizeTable[Protocol.CLAW_BUTTON] = 2;
		msgSizeTable[Protocol.BUTTON_START] = 1;
		msgSizeTable[Protocol.BUTTON_STOP] = 1;
		msgSizeTable[Protocol.REQUEST_IMAGE] = 1;
		msgSizeTable[Protocol.IMG_CALIB_DISK] = 49;
		msgSizeTable[Protocol.IMG_CALIB_MEM] = 49;
		msgSizeTable[Protocol.BUZZER] = 2;
		msgSizeTable[Protocol.TRASH_POSITION] = 17;
		msgSizeTable[Protocol.REQUEST_IMAGE_BACK] = 1;
	}
	
	private int getMessageSize(int messageType)
	{
		int size = msgSizeTable[messageType];
		
		if(messageType == 0x60)
		{			
			if(bufferEnd >= 13)
			{
				ByteBuffer bb = ByteBuffer.allocate(4);
				bb.put(connectionBuffer, 9, 4);
				bb.rewind();
				size = bb.getInt() + 13;
			}
			else
			{
				size = -1;
			}
		}
		else if(messageType == 0x65)
		{
			if(bufferEnd > 1)
			{
				size = (connectionBuffer[1] & 0xFF) + 2;
			}
			else
			{
				size = -1;
			}
		}
		
		return size;
	}
	
	public boolean messagesAvailable()
	{
		if(msgBuffer.size() > 0)
		{
			return true;
		}
		
		return false;
	}
	
	public byte[] getNextMessage()
	{
		if(msgBuffer.size() > 0)
		{
			return msgBuffer.remove(0);
		}		
		
		return null;
	}
	
	private void moveMsgToBuffer(int size)
	{
		byte[] newMsg = new byte[size];
		
		for(int i=0; i<size; i++)
		{
			newMsg[i] = connectionBuffer[i];
		}
		
		msgBuffer.add(newMsg);
		
		bufferEnd = bufferEnd - size;
		for(int i=0; i<bufferEnd; i++)
		{
			connectionBuffer[i] = connectionBuffer[i+size];
		}
		
	}
	
	private void discardBytes(int numberBytes)
	{
		bufferEnd = bufferEnd - numberBytes;
		for(int i=0; i<bufferEnd; i++)
		{
			connectionBuffer[i] = connectionBuffer[i+numberBytes];
		}
	}
	
	private void processMessages()
	{
		boolean limited = false;
		while(bufferEnd > 0 && !limited)
		{			
			int type = connectionBuffer[0] & 0xFF;	// Isto converte bytes negativos para positivo
			
			//Log.i("ASSEMBLER", type+"");
			
			int size = getMessageSize(type);
			if(size == 0)
			{
				discardBytes(1);
			}
			else if(size == -1)
			{
				limited = true;
			}
			else
			{
				if(bufferEnd > size-1)
				{
					moveMsgToBuffer(size);
				}
				else
				{
					limited = true;
				}
			}			
		}
	}
	
	public void listenConnection(CodigoAndroidActivity act)
	{			
		try
		{
			int readSize = connection.readMessageNonBlocking(connectionBuffer, bufferEnd, BUFFER_SIZE-bufferEnd);
			bufferEnd += readSize;
			
			processMessages();
		}
		catch (IOException e)
		{
			//e.printStackTrace();
			act.pcPrint(e.toString());
			act.pcPrint("MessageAssembler");
			disconnected = true;
		}
	}
	
	public void sendImageMessage(byte[] data, int width, int height) throws IOException
	{			
		byte[] cameraCode = {0x60};
		
		ByteBuffer bb = ByteBuffer.allocate(4);		
		bb.putInt(width);
		byte[] widthBytes = bb.array();
		
		bb = ByteBuffer.allocate(4);
		bb.putInt(height);
		byte[] heightBytes = bb.array();
		
		bb = ByteBuffer.allocate(4);
		bb.putInt(data.length);
		byte[] lengthBytes = bb.array();
				
		connection.sendMessage(cameraCode, 0, cameraCode.length);
		connection.sendMessage(widthBytes, 0, widthBytes.length);
		connection.sendMessage(heightBytes, 0, heightBytes.length);
		connection.sendMessage(lengthBytes, 0, lengthBytes.length);
		connection.sendMessage(data, 0, data.length);
		
		Log.i(TAG, "data.length = "+data.length);
		
	}
	
		public void sendAccelerometerMessage(byte[] dataX, byte[] dataY, byte[] dataZ) throws IOException
	{			
		byte[] AccelerometerCode = {0x20};	
		connection.sendMessage(AccelerometerCode, 0, AccelerometerCode.length);
		connection.sendMessage(dataX, 0, dataX.length);
		connection.sendMessage(dataY, 0, dataY.length);
		connection.sendMessage(dataZ, 0, dataZ.length);
		
		//Log.i(TAG, "data.length = "+data.length);
		
	}
	
	public void sendCompassMessage(byte[] dataX, byte[] dataY, byte[] dataZ) throws IOException
	{			
		byte[] CompassCode = {0x21};	
		connection.sendMessage(CompassCode, 0, CompassCode.length);
		connection.sendMessage(dataX, 0, dataX.length);
		connection.sendMessage(dataY, 0, dataY.length);
		connection.sendMessage(dataZ, 0, dataZ.length);
		
		//Log.i(TAG, "data.length = "+data.length);
		
	}
	
	public void sendEncoderMessage(byte[] dataRight, byte[] dataLeft) throws IOException
	{			
		byte[] EncoderCode = {0x40};	
		connection.sendMessage(EncoderCode, 0, EncoderCode.length);
		connection.sendMessage(dataRight, 0, dataRight.length);
		connection.sendMessage(dataLeft, 0, dataLeft.length);
		
		//Log.i(TAG, "data.length = "+data.length);
		
	}
	
	public void sendUltraSoundMessage(byte[] data1, byte[] data2, byte[] data3, byte[] data4, byte[] data5, byte[] data6) throws IOException
	{			
		byte[] usCode = {0x30};	
		connection.sendMessage(usCode, 0, usCode.length);
		connection.sendMessage(data1, 0, data1.length);
		connection.sendMessage(data2, 0, data2.length);
		connection.sendMessage(data3, 0, data3.length);
		connection.sendMessage(data4, 0, data4.length);
		connection.sendMessage(data5, 0, data5.length);
		connection.sendMessage(data6, 0, data6.length);
		
		//Log.i(TAG, "data.length = "+data.length);
	}
	
	public boolean isDisconnected()
	{
		return disconnected;
	}

}
