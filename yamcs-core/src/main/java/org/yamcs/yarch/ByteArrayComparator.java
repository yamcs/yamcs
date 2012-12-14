package org.yamcs.yarch;

import java.util.Comparator;

/*this is shamelessly copied from the java.security.util.ByteArrayLexOrder */
public class ByteArrayComparator implements Comparator<byte[]> {
	public int compare(byte[] a1, byte[] a2) {
		for(int i=0;i<a1.length && i<a2.length;i++) {
			int d=(a1[i]&0xFF)-(a2[i]&0xFF);
			if(d!=0)return d;
		}
		return a1.length-a2.length;
	}
}
