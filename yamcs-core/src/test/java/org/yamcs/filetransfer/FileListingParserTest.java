package org.yamcs.filetransfer;

import org.junit.jupiter.api.Test;
import org.yamcs.ValidationException;
import org.yamcs.YConfiguration;
import org.yamcs.protobuf.RemoteFile;
import org.yamcs.utils.TimestampUtil;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileListingParserTest {

    @Test
    void parseBasicTest() throws ValidationException {
        assertEquals(parse(new BasicListingParser(), "",
                "test\n" +
                        "myfile.txt\n" +
                        "stuff/\n" +
                        "my/dir/"
        ), List.of(
                RemoteFile.newBuilder().setName("my/dir").setIsDirectory(true).build(),
                RemoteFile.newBuilder().setName("stuff").setIsDirectory(true).build(),
                RemoteFile.newBuilder().setName("myfile.txt").setIsDirectory(false).build(),
                RemoteFile.newBuilder().setName("test").setIsDirectory(false).build()
        ));

        assertEquals(parse(new BasicListingParser(), YConfiguration.wrap(Map.of("directoryTerminators", List.of("\\"))), "",
                "test\n" +
                        "myfile.txt\n" +
                        "stuff/\r\n" +
                        "my/dir/\n" +
                        "my\\dir\\\n" +
                        "truedir\\\r\n" +
                        "no\\dir\n"
        ), List.of(
                RemoteFile.newBuilder().setName("my\\dir").setIsDirectory(true).build(),
                RemoteFile.newBuilder().setName("truedir").setIsDirectory(true).build(),
                RemoteFile.newBuilder().setName("my/dir/").setIsDirectory(false).build(),
                RemoteFile.newBuilder().setName("myfile.txt").setIsDirectory(false).build(),
                RemoteFile.newBuilder().setName("no\\dir").setIsDirectory(false).build(),
                RemoteFile.newBuilder().setName("stuff/").setIsDirectory(false).build(),
                RemoteFile.newBuilder().setName("test").setIsDirectory(false).build()
        ));

        assertEquals(parse(new BasicListingParser(), YConfiguration.wrap(Map.of("removePrependingRemotePath", false)),"mydir",
                "test\n" +
                    "file\n" +
                    "mydir\n" +
                    "mydirprefix\n" +
                    "mydir/ok\n" +
                    "mydir/\n"
        ), List.of(
                RemoteFile.newBuilder().setName("mydir").setIsDirectory(true).build(),
                RemoteFile.newBuilder().setName("file").setIsDirectory(false).build(),
                RemoteFile.newBuilder().setName("mydir").setIsDirectory(false).build(),
                RemoteFile.newBuilder().setName("mydir/ok").setIsDirectory(false).build(),
                RemoteFile.newBuilder().setName("mydirprefix").setIsDirectory(false).build(),
                RemoteFile.newBuilder().setName("test").setIsDirectory(false).build()
        ));

        assertEquals(parse(new BasicListingParser(), YConfiguration.wrap(Map.of("removePrependingRemotePath", true)),"mydir",
                "test\n" +
                        "file\n" +
                        "mydir\n" +
                        "mydirprefix\n" +
                        "mydir/ok\n" +
                        "mydir/\n"
        ), List.of(
                RemoteFile.newBuilder().setName("").setIsDirectory(true).build(),
                RemoteFile.newBuilder().setName("").setIsDirectory(false).build(),
                RemoteFile.newBuilder().setName("file").setIsDirectory(false).build(),
                RemoteFile.newBuilder().setName("ok").setIsDirectory(false).build(),
                RemoteFile.newBuilder().setName("prefix").setIsDirectory(false).build(),
                RemoteFile.newBuilder().setName("test").setIsDirectory(false).build()
        ));
    }

    @Test
    void parseCsvTest() throws ValidationException {
        assertEquals(parse(new CsvListingParser(), "",
                "test\n" +
                        "myfile.txt\n" +
                        "stuff/\n" +
                        "my/dir/"
        ), List.of(
                RemoteFile.newBuilder().setName("my/dir/").build(),
                RemoteFile.newBuilder().setName("myfile.txt").build(),
                RemoteFile.newBuilder().setName("stuff/").build(),
                RemoteFile.newBuilder().setName("test").build()
        ));

        assertEquals(parse(new CsvListingParser(), "",
                "test,false,10,1\n" +
                        "myfile.txt,false,10,20\n" +
                        "stuff,true,10,50\n" +
                        "my/dir,true,10,60"
        ), List.of(
                RemoteFile.newBuilder().setName("my/dir").setIsDirectory(true).setSize(10).setModified(TimestampUtil.java2Timestamp(60000)).build(),
                RemoteFile.newBuilder().setName("stuff").setIsDirectory(true).setSize(10).setModified(TimestampUtil.java2Timestamp(50000)).build(),
                RemoteFile.newBuilder().setName("myfile.txt").setIsDirectory(false).setSize(10).setModified(TimestampUtil.java2Timestamp(20000)).build(),
                RemoteFile.newBuilder().setName("test").setIsDirectory(false).setSize(10).setModified(TimestampUtil.java2Timestamp(1000)).build()
        ));

        assertEquals(parse(new CsvListingParser(), "",
                "test,\"false\",10,1\n" +
                        "myfile.txt,false,10,20\n" +
                        "stuff,true, \"10\" ,50\n\n" +
                        "my/dir, true ,10 , 60\r\n" +
                        "\"FILE WITH SPACES\", false , 9001 , 42"
        ), List.of(
                RemoteFile.newBuilder().setName("my/dir").setIsDirectory(true).setSize(10).setModified(TimestampUtil.java2Timestamp(60000)).build(),
                RemoteFile.newBuilder().setName("stuff").setIsDirectory(true).setSize(10).setModified(TimestampUtil.java2Timestamp(50000)).build(),
                RemoteFile.newBuilder().setName("FILE WITH SPACES").setIsDirectory(false).setSize(9001).setModified(TimestampUtil.java2Timestamp(42000)).build(),
                RemoteFile.newBuilder().setName("myfile.txt").setIsDirectory(false).setSize(10).setModified(TimestampUtil.java2Timestamp(20000)).build(),
                RemoteFile.newBuilder().setName("test").setIsDirectory(false).setSize(10).setModified(TimestampUtil.java2Timestamp(1000)).build()
        ));

        assertEquals(parse(new CsvListingParser(), YConfiguration.wrap(Map.of("useCsvHeader", true)), "",
                "isDirectory, \"size\", name, modified\n" +
                        "false,10,test,1\n" +
                        "false,10,myfile.txt,20\n" +
                        "true,10,stuff,50\n" +
                        "true,10,my/dir,60"
        ), List.of(
                RemoteFile.newBuilder().setName("my/dir").setIsDirectory(true).setSize(10).setModified(TimestampUtil.java2Timestamp(60000)).build(),
                RemoteFile.newBuilder().setName("stuff").setIsDirectory(true).setSize(10).setModified(TimestampUtil.java2Timestamp(50000)).build(),
                RemoteFile.newBuilder().setName("myfile.txt").setIsDirectory(false).setSize(10).setModified(TimestampUtil.java2Timestamp(20000)).build(),
                RemoteFile.newBuilder().setName("test").setIsDirectory(false).setSize(10).setModified(TimestampUtil.java2Timestamp(1000)).build()
        ));
    }

    List<RemoteFile> parse(FileListingParser parser, String remotePath, String data) throws ValidationException {
        return parse(parser, YConfiguration.emptyConfig(), remotePath, data);
    }

    List<RemoteFile> parse(FileListingParser parser, YConfiguration config,String remotePath, String data) throws ValidationException {
        config = parser.getSpec().validate(config);
        parser.init("", config);

        return parser.parse(remotePath, data.getBytes());
    }
}
