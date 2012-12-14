package org.yamcs.usoctools;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.PriorityQueue;
import java.util.TimeZone;

import org.yamcs.archive.PacketWithTime;
import org.yamcs.tctm.FileTmPacketProvider;
import org.yamcs.tctm.TmFileReader;

import org.yamcs.utils.CcsdsPacket;

/**
 * Converts a pacts archive into an hrdp one
 * @author nm
 *
 */
public class PactsToHrdpArchive {
	long prevtime=-1;
	final PriorityQueue<CcsdsPacket> queue=new PriorityQueue<CcsdsPacket>();
	final SimpleDateFormat sdf=new SimpleDateFormat("yyyy/DDD/HH/");
	String prevfilename=null;
	FileOutputStream output=null;
	ByteBuffer hrdpheader=ByteBuffer.allocate(10);
	
	final Calendar cal=Calendar.getInstance();

	PactsToHrdpArchive () {
		sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
		cal.setTimeZone(TimeZone.getTimeZone("UTC"));
	}
	
	public void traverseArchive(String path) throws IOException {
		File root=new File(path);
		File[] months=root.listFiles();
		Arrays.sort(months);
		for(File m:months) {
			File[] days=m.listFiles();
			Arrays.sort(days);
			for(File d: days) {
				System.out.println("processing "+d);		
				File[] hours=d.listFiles();
				Arrays.sort(hours);
				for(File h:hours) {
					System.out.println("processing "+h);
					processFile(h);
				}
			}
		}
	}
	
	public void writeLastBits() throws IOException {
		while(queue.size()>0) {
			writePacket(queue.poll());
		}
		System.out.println("All finished!");
	}
	
	public void processRecursively(File file) throws IOException {
		if(file.isDirectory()) {
			File[] files=file.listFiles();
			Arrays.sort(files);
			for (File f:files) {
				processRecursively(f);
			}
		} else {
			//process32BitFile(file);
			processFile(file);
		}
	}
	
	/**this one gets ccsds packets having a 32 bits in front**/
	private void process32BitFile(File f) throws IOException{
		System.out.println("processing "+f);
		InputStream input=new FileInputStream(f);
		byte[] b=new byte[32];
		int r=0;
		while(true) {
			r=input.read(b);
			if(r==-1) return;
			if(r!=32) throw new IOException("Cannot read the pacts 32 bytes header, instead read "+r+" bytes");
			CcsdsPacket p=CcsdsPacket.getPacketFromStream(input);
			processPacket(p);
		}
	}
	
	private void processFile(File f) throws IOException{
		System.out.println("processing "+f);
		TmFileReader p=new TmFileReader(f.toString());
		PacketWithTime  pwrt;
		while((pwrt=p.readPacket())!=null) {
			processPacket(new CcsdsPacket(pwrt.bb));
		}
	}


	private void processPacket(CcsdsPacket p) throws IOException {
		if(p.getPacketID() == 2345) return; //skip the EuTEF H&S
		if(p.getLength()>3000) System.exit(-1);
		if(p.getInstant()<950000000000L) return;
		//System.out.println("adding packet apid: "+p.getAPID()+" packetid: "+p.getPacketID()+" length: "+p.getLength());
		queue.add(p);
		if(queue.size()>300000) {
			writePacket(queue.poll());
		}
	}
	
	private void writePacket(CcsdsPacket p) throws IOException {
		long time=p.getInstant();
		if(prevtime>time) {
			System.err.println("previous time greater than time: "+prevtime+">"+time);
			System.exit(-1);
		}
		prevtime=time;
		String dirname="/home/st/storage/Archive/Sim1/PathTM/PTH/RealTime/"+sdf.format(new Date(time));
		cal.setTimeInMillis(time);
		int minute=cal.get(Calendar.MINUTE);
		minute=5*(minute/5);
		String ms=String.format("%02d", minute);
		String ms4=String.format("%02d", minute+4);
		
		String filename=dirname+"rt_"+ms+"_"+ms4+".dat";
		//System.out.println("writing packet from "+(new Date(time))+" to file"+dirname+filename);
		if(!filename.equals(prevfilename)) {
			prevfilename=filename;
			if(output!=null)output.close();
			File dirFile=new File(dirname);
			if((!dirFile.exists()) && (!dirFile.mkdirs())) {
				throw new IOException("can't create directories "+dirname);
			}
			File f=new File(filename);
			if(f.exists()) {
				throw new IOException("file "+filename+"+already exists, merge is not supported");
			}
			output=new FileOutputStream(f);
		}
		//System.out.println("writing packet apid: "+p.getAPID()+" packetid: "+p.getPacketID()+" length: "+p.getLength());
		
		if(p.getLength()>3000) System.exit(-1);
		hrdpheader.order(ByteOrder.LITTLE_ENDIAN);
		hrdpheader.putInt(0,6+p.getLength());
		hrdpheader.put(4,(byte)9);
		hrdpheader.order(ByteOrder.BIG_ENDIAN);
		hrdpheader.putInt(5,(int)p.getCoarseTime());
		hrdpheader.put(9,(byte)p.getFineTime());
		
		output.write(hrdpheader.array());
		output.write(p.getBytes());
	}

	
	public static void main(String[] args) throws IOException {		
		PactsToHrdpArchive ptha=new PactsToHrdpArchive();
		ptha.processRecursively(new File("/home/st/tmp/erb2/pacts"));
		ptha.writeLastBits();
	}
}
