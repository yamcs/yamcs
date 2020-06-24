package org.yamcs.xtce;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.junit.Test;
import org.yamcs.YConfiguration;
import org.yamcs.xtce.xml.XtceAliasSet;
import org.yamcs.xtceproc.XtceDbFactory;

public class XtceAssemblerTest {
    String name;
    Field field;

    static void writeXtceDb(XtceDb db, String filename, String topSS, Predicate<String> filter) throws IOException {
        String xml = new XtceAssembler().toXtce(db, topSS, filter);
        File f = new File(filename);

        try (FileWriter fw = new FileWriter(f)) {
            fw.write(xml);
        }
    }

    @Test
    public void test1() throws Exception {
        Map<String, Object> m1 = new HashMap<>();
        m1.put("type", "xtce");
        m1.put("spec", "src/test/resources/xtce/BogusSAT-2.xml");

        List<YConfiguration> mdbConfigs1 = Arrays.asList(YConfiguration.wrap(m1));
        XtceDb db1 = XtceDbFactory.createInstance(mdbConfigs1, false, false);

        String xml = new XtceAssembler().toXtce(db1);
        File f = File.createTempFile("test1", ".xml");

        try (FileWriter fw = new FileWriter(f)) {
            fw.write(xml);
        }

        Map<String, Object> m2 = new HashMap<>();
        m2.put("type", "xtce");
        m2.put("spec", f.getAbsolutePath());
        List<YConfiguration> mdbConfigs2 = Arrays.asList(YConfiguration.wrap(m2));
        XtceDb db2 = XtceDbFactory.createInstance(mdbConfigs2, false, false);
        f.delete();

        compareDatabases(db1, db2);
    }

    private void compareDatabases(XtceDb db1, XtceDb db2) throws Exception {
        for (SpaceSystem ss1 : db1.getSpaceSystems()) {
            if (ss1.getName().startsWith(XtceDb.YAMCS_SPACESYSTEM_NAME)) {
                continue;
            }
            SpaceSystem ss2 = db2.getSpaceSystem(ss1.getQualifiedName());
            assertNotNull("Cannot find " + ss1.getQualifiedName() + " in db2", ss2);
            compareSpaceSystems(ss1, ss2);
        }
    }

    private void compareSpaceSystems(SpaceSystem ss1, SpaceSystem ss2) throws Exception {
        for (Parameter p1 : ss1.getParameters()) {
            Parameter p2 = ss2.getParameter(p1.getName());
            assertNotNull("Cannot find " + p1.getQualifiedName() + " in ss2", p2);
            name = p1.getQualifiedName();
            compareObjects(p1, p2);
        }

        for (SequenceContainer sc1 : ss1.getSequenceContainers()) {
            SequenceContainer sc2 = ss2.getSequenceContainer(sc1.getName());
            assertNotNull("Cannot find " + sc1.getQualifiedName() + " in ss2", sc2);
            compareContainer(sc1, sc2);
        }

        for (MetaCommand mc1 : ss1.getMetaCommands()) {
            MetaCommand mc2 = ss2.getMetaCommand(mc1.getName());
            name = mc1.getQualifiedName();
            assertNotNull("Cannot find " + mc1.getQualifiedName() + " in ss2", mc2);
            compareObjects(mc1, mc2);
            compareContainer(mc1.getCommandContainer(), mc2.getCommandContainer());
        }
    }

    private void compareContainer(Container sc1, Container sc2) throws Exception {
        assertEquals(name + ": " + sc1.getQualifiedName() + " has a different number of entries",
                sc1.getEntryList().size(),
                sc2.getEntryList().size());
        for (int i = 0; i < sc1.getEntryList().size(); i++) {
            SequenceEntry se1 = sc1.getEntryList().get(i);
            SequenceEntry se2 = sc2.getEntryList().get(i);
            name = sc1.getName() + " " + se1 + "\n";
            compareObjects(se1, se2);
        }

        if (sc1.getBaseContainer() != null) {
            assertEquals(sc1.getBaseContainer().getQualifiedName(), sc2.getBaseContainer().getQualifiedName());
            if (sc1.getRestrictionCriteria() != null) {
                compareObjects(sc1.getRestrictionCriteria(), sc2.getRestrictionCriteria());
            }
        }
        compareLists(sc1.getAncillaryData(), sc2.getAncillaryData());
    }

    private void compareLists(List<?> l1, List<?> l2) throws Exception {
        if (l1 == null) {
            assertNull(l2);
            return;
        } else {
            assertNotNull(l2);
        }
        
        assertEquals(name, l1.size(), l2.size());
        for (int i = 0; i < l1.size(); i++) {
            compareObjects(l1.get(i), l2.get(i));
        }
    }

    private void compareMaps(Map<?, ?> m1, Map<?, ?> m2) throws Exception {
        if (m1 == null) {
            assertNull(m2);
            return;
        } else {
            assertNotNull(m2);
        }

        for (Map.Entry<?, ?> me : m1.entrySet()) {
            Object v1 = me.getValue();
            Object v2 = m2.get(me.getKey());
            if (v1 == null && v2 != null) {
                fail(name + " value for key " + me.getKey() + " should be null");
            }
            if (v1 != null && v2 == null) {
                fail(name + " value for key " + me.getKey() + " should not be null");
            }
            compareObjects(v1, v2);
        }
    }

    private void compareAliases(XtceAliasSet set1, XtceAliasSet set2) {
        assertTrue(set1.getAliases().equals(set2.getAliases()));
    }

    private void compareObjects(Object o1, Object o2) throws Exception {
        Class c1 = o1.getClass();
        Class c2 = o2.getClass();
        while (true) {
            assertEquals(c1, c2);
            Field[] fa = c1.getDeclaredFields();
            for (Field f : fa) {
                field = f;
                f.setAccessible(true);
                Object o1c = f.get(o1);
                Object o2c = f.get(o2);
                // System.out.println("comparring "+o1.getClass());
                if (o1c == null) {
                    assertNull(name + " " + o1 + " field: " + f.getName(), o2c);
                } else if (o2c == null) {
                    fail(name + " " + o2 + " field: " + f.getName() + " is null, expected " + o1c);
                } else if (o1c instanceof List<?>) {
                    assertTrue(o2c instanceof List);
                    name = name + ": " + f.getName();
                    compareLists((List<?>) o1c, (List<?>) o2c);
                } else if (o1c instanceof Map<?, ?>) {
                    assertTrue(o2c instanceof Map);
                    name = name + ": " + f.getName();
                    compareMaps((Map<?, ?>) o1c, (Map<?, ?>) o2c);
                } else if (o1c instanceof Comparable<?>) {
                    assertEquals(name + " " + o1 + " field: " + f.getName(), o1c, o2c);
                } else if (o1c instanceof ByteOrder) {
                    assertEquals(name + " " + o1 + " field: " + f.getName(), o1c, o2c);
                } else if (o1c instanceof XtceAliasSet) {
                    compareAliases((XtceAliasSet) o1c, (XtceAliasSet) o2c);
                } else if (o1c instanceof MetaCommand || o1c instanceof Container || o1c instanceof Parameter) {
                    // these are already compared above
                } else if (o1c instanceof org.slf4j.impl.JDK14LoggerAdapter) {
                    // ignore
                } else {
                    compareObjects(o1c, o2c);
                }

            }
            c1 = c1.getSuperclass();
            c2 = c2.getSuperclass();
            if (c1 == null) {
                break;
            }
        }
    }

}
