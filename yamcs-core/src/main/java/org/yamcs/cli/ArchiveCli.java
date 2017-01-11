package org.yamcs.cli;

import java.util.List;

import org.rocksdb.RocksDB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.TimeInterval;
import org.yamcs.YConfiguration;
import org.yamcs.cli.YamcsCli.Command;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.rocksdb.HistogramRebuilder;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;


/**
 * Command line utility for doing archive operations
 * 
 * 
 * @author nm
 *
 */
@Parameters(commandDescription = "Archive operations")
public class ArchiveCli extends Command {

    @Parameter(names="--upgrade", description="upgrade archive to the latest version")
    private boolean upgrade;

    @Parameter(names="-h", description="help")
    private boolean helpOp;

    @Parameter(names="--force", description="force rebuild even if the format version looks ok")
    private boolean force;

    @Parameter(names="--instance", description="yamcs instance")
    String yamcsInstance;
    static Logger log = LoggerFactory.getLogger(ArchiveCli.class);

    ArchiveCli() {
        TimeEncoding.setUp();
        RocksDB.loadLibrary();

    }
    public String getUsage() {
        StringBuilder sb = new StringBuilder();
        sb.append( "Usage: \n" );
        sb.append( "\t archive <operation> [parameters] \n" );
        sb.append( "\t operation: \n");
        sb.append( "\t\t --upgrade upgrade archive to the latest version.\n");
        sb.append( "\t\t -h Print this usage information.\n");
        sb.append( "\tparameters: \n" );
        sb.append( "\t\t --instance <yamcsInstance> yamcs instance\n");

        sb.append( "" );
        return sb.toString();
    }

    public void validate() throws ParameterException {
        if(upgrade) {

        } else {
            throw new ParameterException("Please specify one of --upgrade");
        }
    }
    @Override
    public void execute() throws Exception {
        if(upgrade) {
            if(yamcsInstance!=null) {
                if(!YarchDatabase.instanceExistsOnDisk(yamcsInstance)) {
                    throw new ParameterException("Archive instance '"+yamcsInstance+"' does not exist");
                }
                upgradeInstance(yamcsInstance);
            } else {
                List<String> instances =  YConfiguration.getConfiguration("yamcs").getList("instances");
                for(String instance:instances) {
                    upgradeInstance(instance);
                }
            }
        } 
    }

    private void upgradeInstance(String instance) throws Exception {
        upgradeYarchTables(instance);
    }
    private void upgradeYarchTables(String instance) throws Exception {
        YarchDatabase ydb = YarchDatabase.getInstance(instance, true);
        for(TableDefinition tblDef: ydb.getTableDefinitions()) {
            if(tblDef.getFormatVersion()==0) {
                upgrade0_1(ydb, tblDef);
                tblDef.changeFormatDefinition(TableDefinition.CURRENT_FORMAT_VERSION);
            } else {
                log.debug("Skipping table {}/{} because table format version is {}", ydb.getName(), tblDef.getName(), tblDef.getFormatVersion());
            }
        }
    }

    private void upgrade0_1(YarchDatabase ydb, TableDefinition tblDef) throws Exception  {
        log.info("upgrading table {}/{} from version 0 to version 1", ydb.getName(), tblDef.getName());
        if("pp".equals(tblDef.getName())) {
            changePpGroup(ydb, tblDef);
        }

        if(tblDef.hasHistogram()) {
            rebuildHistogram(ydb, tblDef);
        }

    }


    private void changePpGroup(YarchDatabase ydb, TableDefinition tblDef) throws Exception {
        if(tblDef.getColumnDefinition("ppgroup") == null) {
            log.info("Table {}/{} has no ppgroup column", ydb.getName(), tblDef.getName());
            return;
        };
        log.info("Renaming ppgroup -> group column in table {}/{}", ydb.getName(), tblDef.getName());
        tblDef.renameColumn("ppgroup", "group");
    }

    private void rebuildHistogram(YarchDatabase ydb, TableDefinition tblDef) throws Exception {
        HistogramRebuilder hrb = new HistogramRebuilder(ydb, tblDef.getName());
        hrb.rebuild(new TimeInterval()).get();
    }
}