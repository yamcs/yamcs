package org.yamcs.cli;

import java.util.List;
import java.util.concurrent.ExecutionException;

import org.rocksdb.RocksDB;
import org.yamcs.TimeInterval;
import org.yamcs.YConfiguration;
import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchException;
import org.yamcs.yarch.rocksdb.HistogramRebuilder;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;

@Parameters(commandDescription = "Upgrade tables to latest format. It can only be done when the Yamcs server is not running.")
public class ArchiveUpgradeCommand extends Command {
    @Parameter(names="--force", description="force rebuild even if the format version looks ok")
    private boolean force;

    @Parameter(names="--instance", description="yamcs instance", required=true)
    String yamcsInstance;


    public ArchiveUpgradeCommand(ArchiveCli archiveCli) {
        super("upgrade", archiveCli);
    }
    @Override
    public void execute() throws Exception {
        RocksDB.loadLibrary();

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
    private void upgradeInstance(String instance) throws Exception {
        upgradeYarchTables(instance);
    }

    private void upgradeYarchTables(String instance) throws Exception {
        YarchDatabase ydb = YarchDatabase.getInstance(instance, true);
        for(TableDefinition tblDef: ydb.getTableDefinitions()) {
            if(tblDef.getFormatVersion()==0) {
                upgrade0_1(ydb, tblDef);
                upgrade1_2(ydb, tblDef);
                tblDef.changeFormatDefinition(TableDefinition.CURRENT_FORMAT_VERSION);
            } else if(tblDef.getFormatVersion()==1){
                upgrade1_2(ydb, tblDef);
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
    
    private void upgrade1_2(YarchDatabase ydb, TableDefinition tblDef) {
        log.info("upgrading table {}/{} from version 1 to version 2", ydb.getName(), tblDef.getName());
        if("pp".equals(tblDef.getName())) {
            changeParaValueType(tblDef);
        }
    }

    static void changeParaValueType(TableDefinition tblDef) {
        TupleDefinition valueDef = tblDef.getValueDefinition();
        List<ColumnDefinition> l= valueDef.getColumnDefinitions();
        for(int i=0; i<l.size(); i++) {
            ColumnDefinition cd = l.get(i);
            if("org.yamcs.protobuf.Pvalue$ParameterValue".equals(cd.getType().name())) {
                ColumnDefinition cd1 = new ColumnDefinition(cd.getName(), DataType.PARAMETER_VALUE);
                l.set(i,  cd1);
            }
        }
    }
    
    private void changePpGroup(YarchDatabase ydb, TableDefinition tblDef) {
        if(tblDef.getColumnDefinition("ppgroup") == null) {
            log.info("Table {}/{} has no ppgroup column", ydb.getName(), tblDef.getName());
            return;
        }
        log.info("Renaming ppgroup -> group column in table {}/{}", ydb.getName(), tblDef.getName());
        tblDef.renameColumn("ppgroup", "group");
    }

    private void rebuildHistogram(YarchDatabase ydb, TableDefinition tblDef) throws InterruptedException, ExecutionException, YarchException {
        HistogramRebuilder hrb = new HistogramRebuilder(ydb, tblDef.getName());
        hrb.rebuild(new TimeInterval()).get();
    }
}