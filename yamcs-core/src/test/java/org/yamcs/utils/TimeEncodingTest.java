package org.yamcs.utils;

import junit.framework.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Date;

/**
 * Created by msc on 18/11/15.
 */
public class TimeEncodingTest {



    @Test
    public void checkMaxInstantValue()
    {
        TimeEncoding.setUp();

        // Assert that TimeEncoding can encode/decode with MAX_INSTANT
        String sMax = TimeEncoding.toString(TimeEncoding.MAX_INSTANT);
        long decodedMax = TimeEncoding.parse(sMax);
        String sRMax = TimeEncoding.toString(decodedMax);
        Assert.assertTrue(sMax.equals(sRMax));

        // Assert that TimeEncoding fails to encode/decode with MAX_INSTANT + 1
        sMax = TimeEncoding.toString(TimeEncoding.MAX_INSTANT + 1);
        decodedMax = TimeEncoding.parse(sMax);
        sRMax = TimeEncoding.toString(decodedMax);
        Assert.assertFalse(sMax.equals(sRMax));

    }


    @Test
    public void findMaxInstantValue()
    {
        // The algorithm below determines the TimeEncoding.MAX_INSTANT
        // that the TimeEncoding object can encode and decode properlly
        TimeEncoding.setUp();
        boolean foundOverflow = false;
        boolean foundMax = false;
        long offset = 1;
        long base = new Date().getTime();
        while(true) {
            while (!foundOverflow) {
                offset *= 2;
                String sMax = TimeEncoding.toString(base + offset);
                long decodedMax = TimeEncoding.parse(sMax);
                String sRMax = TimeEncoding.toString(decodedMax);

                if (!sMax.equals(sRMax)) {
                    foundOverflow = true;
                }
            }
            //System.out.println("overflow, offset = " + offset);
            if(offset <= 2)
            {
                break;
            }
            base = base + offset / 2;
            offset = 1;
            foundOverflow = false;

            //System.out.println("current max = " + base);
            //System.out.println(TimeEncoding.toString(base));
        }

        while(!foundMax)
        {
            base++;
            String sMax = TimeEncoding.toString(base);
            long decodedMax = TimeEncoding.parse(sMax);
            String sRMax = TimeEncoding.toString(decodedMax);

            if (!sMax.equals(sRMax)) {
                base--;
                foundMax = true;
            }
        }
        System.out.println("max = " + base);
        System.out.println(TimeEncoding.toString(base));


        Assert.assertEquals(base, TimeEncoding.MAX_INSTANT);
    }



}
