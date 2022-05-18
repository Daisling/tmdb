package drz.oddb.Transaction;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import drz.oddb.Log.*;
import drz.oddb.Memory.*;


import drz.oddb.R;
import drz.oddb.show.PrintResult;
import drz.oddb.show.ShowTable;
import drz.oddb.show.Showmap;
import drz.oddb.Transaction.SystemTable.*;

import drz.oddb.parse.*;

public class TransAction {
    public TransAction(Context context) {
        this.context = context;
        RedoRest();
    }

    Context context;
    public TupleList locationtpl = new TupleList();


    public MemManage mem = new MemManage();

    public ObjectTable topt = mem.loadObjectTable();
    public ClassTable classt = mem.loadClassTable();
    public DeputyTable deputyt = mem.loadDeputyTable();
    public BiPointerTable biPointerT = mem.loadBiPointerTable();
    public SwitchingTable switchingT = mem.loadSwitchingTable();

    LogManage log = new LogManage(this);

    public void SaveAll() {
        mem.saveObjectTable(topt);
        mem.saveClassTable(classt);
        mem.saveDeputyTable(deputyt);
        mem.saveBiPointerTable(biPointerT);
        mem.saveSwitchingTable(switchingT);
        mem.saveLog(log.LogT);
        while (!mem.flush()) ;
        while (!mem.setLogCheck(log.LogT.logID)) ;
        mem.setCheckPoint(log.LogT.logID);//成功退出,所以新的事务块一定全部执行
    }

    public void Test() {
        TupleList tpl = new TupleList();
        Tuple t1 = new Tuple();
        t1.tupleHeader = 5;
        t1.tuple = new Object[t1.tupleHeader];
        t1.tuple[0] = "a";
        t1.tuple[1] = 1;
        t1.tuple[2] = "b";
        t1.tuple[3] = 3;
        t1.tuple[4] = "e";
        Tuple t2 = new Tuple();
        t2.tupleHeader = 5;
        t2.tuple = new Object[t2.tupleHeader];
        t2.tuple[0] = "d";
        t2.tuple[1] = 2;
        t2.tuple[2] = "e";
        t2.tuple[3] = 2;
        t2.tuple[4] = "v";

        tpl.addTuple(t1);
        tpl.addTuple(t2);
        String[] attrname = {"attr2", "attr1", "attr3", "attr5", "attr4"};
        int[] attrid = {1, 0, 2, 4, 3};
        String[] attrtype = {"int", "char", "char", "char", "int"};

        PrintSelectResult(tpl, attrname, attrid, attrtype);

        int[] a = InsertTuple(t1);
        Tuple t3 = GetTuple(a[0], a[1]);
        int[] b = InsertTuple(t2);
        Tuple t4 = GetTuple(b[0], b[1]);
        System.out.println(t3);
    }

    private boolean RedoRest() {//redo
        LogTable redo;
        if ((redo = log.GetReDo()) != null) {
            int redonum = redo.logTable.size();   //先把redo指令加前面
            for (int i = 0; i < redonum; i++) {
                String s = redo.logTable.get(i).str;

                log.WriteLog(s);
                query(s);
            }
        } else {
            return false;
        }
        return true;
    }

    public String query(String s) {

        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(s.getBytes());
        parse p = new parse(byteArrayInputStream);
        try {
            String[] aa = p.Run();

            switch (Integer.parseInt(aa[0])) {
                case parse.OPT_CREATE_ORIGINCLASS:
                    log.WriteLog(s);
                    CreateOriginClass(aa);
                    new AlertDialog.Builder(context).setTitle("提示").setMessage("创建成功").setPositiveButton("确定", null).show();
                    break;
                case parse.OPT_CREATE_SELECTDEPUTY:
                    log.WriteLog(s);
                    CreateSelectDeputy(aa);
                    new AlertDialog.Builder(context).setTitle("提示").setMessage("创建成功").setPositiveButton("确定", null).show();
                    break;
                case parse.OPT_UNIONDEPUTY:
                    log.WriteLog(s);
                    CreateUnionDeputy(aa);
                    new AlertDialog.Builder(context).setTitle("提示").setMessage("创建成功").setPositiveButton("确定", null).show();
                    break;
                case parse.OPT_DROP:
                    log.WriteLog(s);
                    Drop(aa);
                    new AlertDialog.Builder(context).setTitle("提示").setMessage("删除成功").setPositiveButton("确定", null).show();
                    break;
                case parse.OPT_INSERT:
                    log.WriteLog(s);
                    Insert(aa);
                    new AlertDialog.Builder(context).setTitle("提示").setMessage("插入成功").setPositiveButton("确定", null).show();
                    break;
                case parse.OPT_DELETE:
                    log.WriteLog(s);
                    Delete(aa);
                    new AlertDialog.Builder(context).setTitle("提示").setMessage("删除成功").setPositiveButton("确定", null).show();
                    break;
                case parse.OPT_SELECT_DERECTSELECT:
                    DirectSelect(aa);
                    break;
                case parse.OPT_SELECT_INDERECTSELECT:
                    InDirectSelect(aa);
                    break;
                case parse.OPT_CREATE_UPDATE:
                    log.WriteLog(s);
                    Update(aa);
                    new AlertDialog.Builder(context).setTitle("提示").setMessage("更新成功").setPositiveButton("确定", null).show();
                default:
                    break;

            }
        } catch (ParseException e) {

            e.printStackTrace();
        }

        return s;

    }

    //CREATE CLASS dZ123 (nB1 int,nB2 char) ;
    //1,2,dZ123,nB1,int,nB2,char
    private void CreateOriginClass(String[] p) {
        String classname = p[2];
        int count = Integer.parseInt(p[1]);
        classt.maxid++;
        int classid = classt.maxid;
        for (int i = 0; i < count; i++) {
            classt.classTable.add(new ClassTableItem(classname, classid, count, i, p[2 * i + 3], p[2 * i + 4], "ori"));
        }
    }

    //INSERT INTO aa VALUES (1,2,"3");
    //4,3,aa,1,2,"3"
    //0 1 2  3 4  5
    private int Insert(String[] p) {


        int count = Integer.parseInt(p[1]);
        for (int o = 0; o < count + 3; o++) {
            p[o] = p[o].replace("\"", "");
        }

        String classname = p[2];
        Object[] tuple_ = new Object[count];

        int classid = 0;

        for (ClassTableItem item : classt.classTable) {
            if (item.classname.equals(classname)) {
                classid = item.classid;
            }
        }

        for (int j = 0; j < count; j++) {
            tuple_[j] = p[j + 3];
        }

        Tuple tuple = new Tuple(tuple_);
        tuple.tupleHeader = count;

        int[] a = InsertTuple(tuple);
        topt.maxTupleId++;
        int tupleid = topt.maxTupleId;
        topt.objectTable.add(new ObjectTableItem(classid, tupleid, a[0], a[1]));

        //向代理类加元组

        for (DeputyTableItem item : deputyt.deputyTable) {
            if (classid == item.originid) {
                //判断代理规则

                String attrtype = null;
                int attrid = 0;
                for (ClassTableItem item1 : classt.classTable) {
                    if (item1.classid == classid && item1.attrname.equals(item.deputyrule[0])) {
                        attrtype = item1.attrtype;
                        attrid = item1.attrid;
                        break;
                    }
                }

                if (Condition(attrtype, tuple, attrid, item.deputyrule[2])) {
                    String[] ss = p.clone();
                    String s1 = null;

                    for (ClassTableItem item2 : classt.classTable) {
                        if (item2.classid == item.deputyid) {
                            s1 = item2.classname;
                            break;
                        }
                    }
                    //是否要插switch的值
                    //收集源类属性名
                    String[] attrname1 = new String[count];
                    int[] attrid1 = new int[count];
                    int k = 0;
                    for (ClassTableItem item3 : classt.classTable) {
                        if (item3.classid == classid) {
                            attrname1[k] = item3.attrname;
                            attrid1[k] = item3.attrid;
                            k++;

                            if (k == count)
                                break;
                        }
                    }
                    for (int l = 0; l < count; l++) {
                        for (SwitchingTableItem item4 : switchingT.switchingTable) {
                            if (item4.attr.equals(attrname1[l])) {
                                //判断被置换的属性是否是代理类的

                                for (ClassTableItem item8 : classt.classTable) {
                                    if (item8.attrname.equals(item4.deputy) && Integer.parseInt(item4.rule) != 0) {
                                        if (item8.classid == item.deputyid) {
                                            int sw = Integer.parseInt(p[3 + attrid1[l]]);
                                            ss[3 + attrid1[l]] = new Integer(sw + Integer.parseInt(item4.rule)).toString();
                                            break;
                                        }
                                    }
                                }


                            }
                        }
                    }

                    ss[2] = s1;
                    int deojid = Insert(ss);
                    //插入Bi
                    biPointerT.biPointerTable.add(new BiPointerTableItem(classid, tupleid, item.deputyid, deojid));


                }
            }
        }
        return tupleid;


    }

    private boolean Condition(String attrtype, Tuple tuple, int attrid, String value1) {
        String value = value1.replace("\"", "");
        switch (attrtype) {
            case "int":
                int value_int = Integer.parseInt(value);
                if (Integer.parseInt((String) tuple.tuple[attrid]) == value_int)
                    return true;
                break;
            case "char":
                String value_string = value;
                if (tuple.tuple[attrid].equals(value_string))
                    return true;
                break;

        }
        return false;
    }

    //DELETE FROM bb WHERE t4="5SS";
    //5,bb,t4,=,"5SS"
    private void Delete(String[] p) {
        String classname = p[1];
        String attrname = p[2];
        int classid = 0;
        int attrid = 0;
        String attrtype = null;
        for (ClassTableItem item : classt.classTable) {
            if (item.classname.equals(classname) && item.attrname.equals(attrname)) {
                classid = item.classid;
                attrid = item.attrid;
                attrtype = item.attrtype;
                break;
            }
        }
        //寻找需要删除的
        OandB ob2 = new OandB();
        for (Iterator it1 = topt.objectTable.iterator(); it1.hasNext(); ) {
            ObjectTableItem item = (ObjectTableItem) it1.next();
            if (item.classid == classid) {
                Tuple tuple = GetTuple(item.blockid, item.offset);
                if (Condition(attrtype, tuple, attrid, p[4])) {
                    //需要删除的元组
                    OandB ob = new OandB(DeletebyID(item.tupleid));
                    for (ObjectTableItem obj : ob.o) {
                        ob2.o.add(obj);
                    }
                    for (BiPointerTableItem bip : ob.b) {
                        ob2.b.add(bip);
                    }

                }
            }
        }
        for (ObjectTableItem obj : ob2.o) {
            topt.objectTable.remove(obj);
        }
        for (BiPointerTableItem bip : ob2.b) {
            biPointerT.biPointerTable.remove(bip);
        }

    }

    private OandB DeletebyID(int id) {

        List<ObjectTableItem> todelete1 = new ArrayList<>();
        List<BiPointerTableItem> todelete2 = new ArrayList<>();
        OandB ob = new OandB(todelete1, todelete2);
        for (Iterator it1 = topt.objectTable.iterator(); it1.hasNext(); ) {
            ObjectTableItem item = (ObjectTableItem) it1.next();
            if (item.tupleid == id) {
                //需要删除的tuple


                //删除代理类的元组
                int deobid = 0;

                for (Iterator it = biPointerT.biPointerTable.iterator(); it.hasNext(); ) {
                    BiPointerTableItem item1 = (BiPointerTableItem) it.next();
                    if (item.tupleid == item1.deputyobjectid) {
                        //it.remove();
                        if (!todelete2.contains(item1))
                            todelete2.add(item1);
                    }
                    if (item.tupleid == item1.objectid) {
                        deobid = item1.deputyobjectid;
                        OandB ob2 = new OandB(DeletebyID(deobid));

                        for (ObjectTableItem obj : ob2.o) {
                            if (!todelete1.contains(obj))
                                todelete1.add(obj);
                        }
                        for (BiPointerTableItem bip : ob2.b) {
                            if (!todelete2.contains(bip))
                                todelete2.add(bip);
                        }

                        //biPointerT.biPointerTable.remove(item1);

                    }
                }


                //删除自身
                DeleteTuple(item.blockid, item.offset);
                if (!todelete2.contains(item)) ;
                todelete1.add(item);


            }
        }

        return ob;
    }

    //DROP CLASS asd;
    //3,asd

    private void Drop(String[] p) {
        List<DeputyTableItem> dti;
        dti = Drop1(p);
        for (DeputyTableItem item : dti) {
            deputyt.deputyTable.remove(item);
        }
    }

    private List<DeputyTableItem> Drop1(String[] p) {
        String classname = p[1];
        int classid = 0;
        //找到classid顺便 清除类表和switch表
        for (Iterator it1 = classt.classTable.iterator(); it1.hasNext(); ) {
            ClassTableItem item = (ClassTableItem) it1.next();
            if (item.classname.equals(classname)) {
                classid = item.classid;
                for (Iterator it = switchingT.switchingTable.iterator(); it.hasNext(); ) {
                    SwitchingTableItem item2 = (SwitchingTableItem) it.next();
                    if (item2.attr.equals(item.attrname) || item2.deputy.equals(item.attrname)) {
                        it.remove();
                    }
                }
                it1.remove();
            }
        }
        //清元组表同时清了bi
        OandB ob2 = new OandB();
        for (ObjectTableItem item1 : topt.objectTable) {
            if (item1.classid == classid) {
                OandB ob = DeletebyID(item1.tupleid);
                for (ObjectTableItem obj : ob.o) {
                    ob2.o.add(obj);
                }
                for (BiPointerTableItem bip : ob.b) {
                    ob2.b.add(bip);
                }
            }
        }
        for (ObjectTableItem obj : ob2.o) {
            topt.objectTable.remove(obj);
        }
        for (BiPointerTableItem bip : ob2.b) {
            biPointerT.biPointerTable.remove(bip);
        }

        //清deputy
        List<DeputyTableItem> dti = new ArrayList<>();
        for (DeputyTableItem item3 : deputyt.deputyTable) {
            if (item3.deputyid == classid) {
                if (!dti.contains(item3))
                    dti.add(item3);
            }
            if (item3.originid == classid) {
                //删除代理类
                String[] s = p.clone();
                List<String> sname = new ArrayList<>();
                for (ClassTableItem item5 : classt.classTable) {
                    if (item5.classid == item3.deputyid) {
                        sname.add(item5.classname);
                    }
                }
                for (String item4 : sname) {

                    s[1] = item4;
                    List<DeputyTableItem> dti2 = Drop1(s);
                    for (DeputyTableItem item8 : dti2) {
                        if (!dti.contains(item8))
                            dti.add(item8);
                    }

                }
                if (!dti.contains(item3))
                    dti.add(item3);
            }
        }
        return dti;

    }


    //SELECT  b1+2 AS c1,b2 AS c2,b3 AS c3 FROM  bb WHERE t1="1";
    //6,3,b1,1,2,c1,b2,0,0,c2,b3,0,0,c3,bb,t1,=,"1"
    //0 1 2  3 4 5  6  7 8 9  10 111213 14 15 16 17
    private TupleList DirectSelect(String[] p) {
        TupleList tpl = new TupleList();
        int attrnumber = Integer.parseInt(p[1]);
        String[] attrname = new String[attrnumber];
        int[] attrid = new int[attrnumber];
        String[] attrtype = new String[attrnumber];
        String classname = p[2 + 4 * attrnumber];
        int classid = 0;
        for (int i = 0; i < attrnumber; i++) {
            for (ClassTableItem item : classt.classTable) {
                //在class表中找到属性
                if (item.classname.equals(classname) && item.attrname.equals(p[2 + 4 * i])) {
                    classid = item.classid;
                    attrid[i] = item.attrid;
                    attrtype[i] = item.attrtype;
                    attrname[i] = p[5 + 4 * i];
                    //重命名

                    break;
                }
            }
        }


        int sattrid = 0;
        String sattrtype = null;
        //找到规则属性
        for (ClassTableItem item : classt.classTable) {
            if (item.classid == classid && item.attrname.equals(p[3 + 4 * attrnumber])) {
                sattrid = item.attrid;
                sattrtype = item.attrtype;
                break;
            }
        }


        for (ObjectTableItem item : topt.objectTable) {
            if (item.classid == classid) {
                Tuple tuple = GetTuple(item.blockid, item.offset);

                if (Condition(sattrtype, tuple, sattrid, p[4 * attrnumber + 5])) {
                    //Switch

                    for (int j = 0; j < attrnumber; j++) {
                        if (Integer.parseInt(p[3 + 4 * j]) == 1) {

                            int value = Integer.parseInt(p[4 + 4 * j]);

                            int orivalue = Integer.parseInt((String) tuple.tuple[attrid[j]]);

                            Object ob = value + orivalue;

                            tuple.tuple[attrid[j]] = ob;

                        }

                    }
                    //tuplelist中加入


                    tpl.addTuple(tuple);
                    locationtpl.addTuple(tuple);
                }
            }
        }
        for (int i = 0; i < attrnumber; i++) {
            attrid[i] = i;
        }

        PrintSelectResult(tpl, attrname, attrid, attrtype);

        return tpl;

    }


    //CREATE SELECTDEPUTY aa SELECT  b1+2 AS c1,b2 AS c2,b3 AS c3 FROM  bb WHERE t1="1" ;
    //2,3,aa,b1,1,2,c1,b2,0,0,c2,b3,0,0,c3,bb,t1,=,"1"
    //0 1 2  3  4 5 6  7  8 9 10 11 121314 15 16 17 18
    private void CreateSelectDeputy(String[] p) {
        int count = Integer.parseInt(p[1]);
        String classname = p[2];//代理类的名字
        String bedeputyname = p[4 * count + 3];//代理的类的名字
        classt.maxid++;
        int classid = classt.maxid;//代理类的id
        int bedeputyid = -1;//代理的类的id
        String[] attrname = new String[count];
        String[] bedeputyattrname = new String[count];
        int[] bedeputyattrid = new int[count];
        String[] attrtype = new String[count];
        int[] attrid = new int[count];


        for (int j = 0; j < count; j++) {
            attrname[j] = p[4 * j + 6];
            attrid[j] = j;
            bedeputyattrname[j] = p[4 * j + 3];
        }

        for (int i = 0; i < count; i++) {

            for (ClassTableItem item : classt.classTable) {
                if (item.classname.equals(bedeputyname) && item.attrname.equals(p[3 + 4 * i])) {
                    bedeputyid = item.classid;
                    bedeputyattrid[i] = item.attrid;

                    classt.classTable.add(new ClassTableItem(classname, classid, count, attrid[i], attrname[i], item.attrtype, "de"));
                    //swi
                    if (Integer.parseInt(p[4 + 4 * i]) == 1) {
                        switchingT.switchingTable.add(new SwitchingTableItem(item.attrname, attrname[i], p[5 + 4 * i]));
                    }
                    if (Integer.parseInt(p[4 + 4 * i]) == 0) {
                        switchingT.switchingTable.add(new SwitchingTableItem(item.attrname, attrname[i], "0"));
                    }
                    break;
                }
            }
            ;
        }


        String[] con = new String[3];
        con[0] = p[4 + 4 * count];
        con[1] = p[5 + 4 * count];
        con[2] = p[6 + 4 * count];
        deputyt.deputyTable.add(new DeputyTableItem(bedeputyid, classid, con));


        TupleList tpl = new TupleList();

        int conid = 0;
        String contype = null;
        for (ClassTableItem item3 : classt.classTable) {
            //感觉这里有问题，为啥不加if(item2.classid ==bedeputyid)
            if (item3.classid == bedeputyid) {
                if (item3.attrname.equals(con[0])) {
                    conid = item3.attrid;
                    contype = item3.attrtype;
                    break;
                }
            }
        }
        List<ObjectTableItem> obj = new ArrayList<>();
        for (ObjectTableItem item2 : topt.objectTable) {
            if (item2.classid == bedeputyid) {
                Tuple tuple = GetTuple(item2.blockid, item2.offset);
                if (Condition(contype, tuple, conid, con[2])) {
                    //插入
                    //swi
                    Tuple ituple = new Tuple();
                    ituple.tupleHeader = count;
                    ituple.tuple = new Object[count];

                    for (int o = 0; o < count; o++) {
                        if (Integer.parseInt(p[4 + 4 * o]) == 1) {
                            int value = Integer.parseInt(p[5 + 4 * o]);
                            int orivalue = Integer.parseInt((String) tuple.tuple[bedeputyattrid[o]]);
                            Object ob = value + orivalue;
                            ituple.tuple[o] = ob;
                        }
                        if (Integer.parseInt(p[4 + 4 * o]) == 0) {

                            ituple.tuple[o] = tuple.tuple[bedeputyattrid[o]];

                        }
                    }

                    topt.maxTupleId++;
                    int tupid = topt.maxTupleId;

                    int[] aa = InsertTuple(ituple);
                    //topt.objectTable.add(new ObjectTableItem(classid,tupid,aa[0],aa[1]));
                    obj.add(new ObjectTableItem(classid, tupid, aa[0], aa[1]));

                    //bi
                    biPointerT.biPointerTable.add(new BiPointerTableItem(bedeputyid, item2.tupleid, classid, tupid));

                }
            }
        }
        for (ObjectTableItem item6 : obj) {
            topt.objectTable.add(item6);
        }
    }

    //CREATE SELECTDEPUTY aa SELECT  b1+2 AS c1,b2 AS c2,b3 AS c3 FROM  bb WHERE t1="1" ;
    //2,3,aa,b1,1,2,c1,b2,0,0,c2,b3,0,0,c3,bb,t1,=,"1"
    //0 1 2  3  4 5 6  7  8 9 10 11 121314 15 16 17 18
    // 前面一定有3个 中间一定是4的倍数 最后一定有四个 （学长设计的必须有where)
    // union直接加在后面得了
    //  define  UNIONDEPUTY=9
    //CREATE UNIONDEPUTY aa SELECT  b1+2 AS c1,b2 AS c2,b3 AS c3 FROM  bb WHERE t1="1" UNION SELECT  b1+2 AS c1,b2 AS c2,b3 AS c3 FROM  cc WHERE t2="2";
    //9,3,aa,b1,1,2,c1,b2,0,0,c2,b3,0,0,c3,bb,t1,=,"1", b1,1,2,c1,b2,0,0,c2,b3,0,0,c3,cc,t2,=,"2"
    //0 1 2  3  4 5 6  7  8 9 10 11 121314 15 16 17 18  19 202122 23 242526 27 282930 31 32 33 34
    private void CreateUnionDeputy(String[] p) {
        int count = Integer.parseInt(p[1]);
        String classname = p[2];//代理类的名字

        String bedeputyname1 = p[4 * count + 3];//被代理的类的名字1      bb
        String bedeputyname2 = p[2 * 4 * (count) + 4 + 3];//被代理的类的名字2  cc


        classt.maxid++;
        int classid = classt.maxid;//代理类的id

        int bedeputyid1 = -1;//被代理的类的id1

        int bedeputyid2 = -1;//被代理的类的id2


        String[] attrname = new String[count];//代理类的属性

        String[] bedeputyattrname1 = new String[count];//被代理类的属性名1
        String[] bedeputyattrname2 = new String[count];//被代理类的属性名2

        int[] bedeputyattrid1 = new int[count];//被代理类的id1

        int[] bedeputyattrid2 = new int[count];//被代理类的id2

        String[] attrtype = new String[count];//属性类型，int or char

        int[] attrid = new int[count];// 代理类的属性id


        for (int j = 0; j < count; j++) {
            attrname[j] = p[4 * j + 6];
            attrid[j] = j;
            bedeputyattrname1[j] = p[4 * j + 3];
            bedeputyattrname2[j] = p[4 * count + 4 + 3 + 4 * j];
        }

        //好像class表里面只搞了表属性，如果没有就不插

        //count = 3
        //如i=1时，找到第二个被代理的，找到后，就把第二个代理属性插入class
        for (int i = 0; i < count; i++) {

            //对于classt中的每个表项
            for (ClassTableItem item : classt.classTable) {
                //找到要被代理的那个表项
                //表项的属性名为被代理的属性名 = 表项类名等于被代理的类名1
                //相当于每个属性存了一个表项，如果找到被代理类1中的被代理属性，就赋值被代理的类1的id和被代理类的属性id
                //方便以后找到被代理类

                if (item.classname.equals(bedeputyname1) && item.attrname.equals(p[3 + 4 * i])) {

                    bedeputyid1 = item.classid;//被代理的类1的id = calssid

                    bedeputyattrid1[i] = item.attrid; //被代理类的属性id = 表项的属性id


                    //将代理类的属性信息插入系统表
                    //代理类的类名，类id, 属性个数，属性在类表项中的编号，属性名，属性类型(只有两种int or char)
                    //插入这个东西感觉只搞一遍就可以
                    classt.classTable.add(new ClassTableItem(classname, classid, count, attrid[i], attrname[i], item.attrtype, "de"));

                    //原属性，代理属性名，规则
                    //如果是union的话后面代理属性名是一样的
                    //第一个不一样要不要写两遍
                    if (Integer.parseInt(p[4 + 4 * i]) == 1) {
                        switchingT.switchingTable.add(new SwitchingTableItem(item.attrname, attrname[i], p[5 + 4 * i]));
                    }

                    if (Integer.parseInt(p[4 + 4 * i]) == 0) {
                        switchingT.switchingTable.add(new SwitchingTableItem(item.attrname, attrname[i], "0"));
                    }
                    break;

                }


            }
        }


        for (int i = 0; i < count; i++) {
            for (ClassTableItem item : classt.classTable) {

                if (item.classname.equals(bedeputyname2) && item.attrname.equals(p[4 * count + 4 + 3 + 4 * i])) {

                    bedeputyid2 = item.classid;//被代理的类2的id = calssid

                    bedeputyattrid2[i] = item.attrid; //被代理类的属性id = 表项的属性id


                    //原属性，代理属性名，规则
                    //如果是union的话后面代理属性名是一样的
                    //第一个不一样要不要写两遍
                    if (Integer.parseInt(p[4 * count + 8 + 4 * i]) == 1) {
                        switchingT.switchingTable.add(new SwitchingTableItem(item.attrname, attrname[i], p[5 + 4 * i]));
                    }

                    if (Integer.parseInt(p[4 * count + 8 + 4 * i]) == 0) {
                        switchingT.switchingTable.add(new SwitchingTableItem(item.attrname, attrname[i], "0"));
                    }
                    break;


                }

            }


        }


        //无脑写的话，前面的for循环已经搞完了class表和第一个选择代理的swi表
        //那么，可能还需遍历一个for count ,赋值被代理2的id , 和被代理2的属性id列表

        //t1='1'对于第一个代理而言

        String[] con1 =new String[3];
        con1[0] = p[4+4*count];
        con1[1] = p[5+4*count];
        con1[2] = p[6+4*count];
        String[] con2 =new String[3];
        con2[0] = p[8*count+4+4];
        con2[1] = p[8*count+4+5];
        con2[2] = p[8*count+4+6];


        //union的话这里应该要加两个，除了代理类id相同，其他不一样
        deputyt.deputyTable.add(new DeputyTableItem(bedeputyid1,classid,con1));
        deputyt.deputyTable.add(new DeputyTableItem(bedeputyid2,classid,con2));


        TupleList tpl= new TupleList();


        int conid1 = 0;
        String contype1  = null;

        int conid2 = 0;
        String contype2  = null;
        //对于class表里面的
        //t1='1'

        for(ClassTableItem item3:classt.classTable){
            //找到属性名是t1的表项
            //找到t1的属性号，和属性类型,用于后面判断元组是否满足条件
            if(item3.classid ==bedeputyid1){
                if(item3.attrname.equals(con1[0])){
                    conid1 = item3.attrid;
                    contype1 = item3.attrtype;
                    break;
                }
            }
        }

        for(ClassTableItem item4:classt.classTable){
            //找到属性名是t1的表项
            //找到t1的属性号，和属性类型,用于后面判断元组是否满足条件
            if(item4.classid ==bedeputyid2){
                if(item4.attrname.equals(con2[0])){
                    conid2 = item4.attrid;
                    contype2 = item4.attrtype;
                    break;
                }
            }
        }

        List<Tuple> tuplelist1 = new ArrayList<>();
        List<Integer> tupleid1 = new ArrayList<>();
        List<Tuple> tuplelist2 = new ArrayList<>();
        List<Integer> tupleid2 = new ArrayList<>();

        for(ObjectTableItem item2:topt.objectTable){
            if(item2.classid == bedeputyid1){

                Tuple tuple1 = GetTuple(item2.blockid,item2.offset);

                if(Condition(contype1,tuple1,conid1,con1[2])){
                    tuplelist1.add(tuple1);
                    tupleid1.add(item2.tupleid);

                }


            }
            if(item2.classid == bedeputyid2){

                Tuple tuple2 = GetTuple(item2.blockid,item2.offset);

                if(Condition(contype2,tuple2,conid2,con2[2])){
                    tuplelist2.add(tuple2);
                    tupleid2.add(item2.tupleid);
                }


            }
        }

        List<Tuple> ituplelist2 = new ArrayList<>();
        for(Tuple tuple2:tuplelist2){

            //插入
            //swi

            Tuple ituple2 = new Tuple();

            ituple2.tupleHeader = count;
            ituple2.tuple = new Object[count];
            //插入元组每条属性

            for(int o =0;o<count;o++){
                //需要计算的
                //这里可能搞错了
                if(Integer.parseInt(p[4 * count + 8 + 4 * o]) == 1){
                    //value是加数

                    int value = Integer.parseInt(p[4 * count + 9 + 4 * o]);

                    int orivalue =Integer.parseInt((String)tuple2.tuple[bedeputyattrid2[o]]);

                    Object ob = value+orivalue;

                    ituple2.tuple[o] = ob;


                }
                //不需要计算

                if(Integer.parseInt(p[4 * count + 8 + 4 * o]) == 0){

                    //取出被代理的属性id bedeputyattrid[o] 这个是在插代理类的时候存入的
                    ituple2.tuple[o] = tuple2.tuple[bedeputyattrid2[o]];

                }

            }
            ituplelist2.add(ituple2);


        }


        List<Tuple> ituplelist1 = new ArrayList<>();
        for(Tuple tuple1:tuplelist1){

            //插入
            //swi

            Tuple ituple1 = new Tuple();

            ituple1.tupleHeader = count;
            ituple1.tuple = new Object[count];
            //插入元组每条属性

            for(int o =0;o<count;o++){
                //需要计算的
                if(Integer.parseInt(p[4+4*o]) == 1){
                    //value是加数

                    int value = Integer.parseInt(p[5+4*o]);

                    int orivalue =Integer.parseInt((String)tuple1.tuple[bedeputyattrid1[o]]);

                    Object ob = value+orivalue;

                    ituple1.tuple[o] = ob;


                }
                //不需要计算

                if(Integer.parseInt(p[4+4*o]) == 0){

                    //取出被代理的属性id bedeputyattrid[o] 这个是在插代理类的时候存入的
                    ituple1.tuple[o] = tuple1.tuple[bedeputyattrid1[o]];


                }

            }
            ituplelist1.add(ituple1);


        }
        Iterator<Tuple> tlist1 = ituplelist1.iterator();
        Iterator<Integer> idlist1 = tupleid1.iterator();

        Iterator<Tuple> tlist2 = ituplelist2.iterator();
        Iterator<Integer> idlist2 = tupleid2.iterator();


        List<Tuple> deltuplelist1 = new ArrayList<>();
        List<Integer> delidlist1 = new ArrayList<>();

        while(tlist1.hasNext()&&idlist1.hasNext()){
            Tuple tuple1 = tlist1.next();
            Integer id1 = idlist1.next();



            //对于tuple1中的每一个属性都遍历一遍
            boolean flag=false;



            //反正tuple2要全插，在前面插入了

            while(tlist2.hasNext()){
                //如果有一个相同，就接着判断后面的属性是否相同
                Tuple tuple2 = tlist2.next();


                if(tuple2.tuple[0].equals(tuple1.tuple[0])){

                    for(int i =1;i<count;i++){
                        if(tuple1.tuple[i].equals(tuple2.tuple[i])){
                            flag=false;
                        }
                        else{
                            flag=true;
                            break;
                        }

                    }

                    if(flag == false){
                        delidlist1.add(id1);
                        deltuplelist1.add(tuple1);

                    }

                }


            }

            tlist2 = ituplelist2.iterator();



        }
        ituplelist1.removeAll(deltuplelist1);
        tupleid1.removeAll(delidlist1);

        tlist1 = ituplelist1.iterator();
        idlist1 = tupleid1.iterator();

        tlist2 = ituplelist2.iterator();
        idlist2 = tupleid2.iterator();

        while(tlist1.hasNext()&&idlist1.hasNext()){

            topt.maxTupleId++;
            int tupid = topt.maxTupleId;
            int [] aa = InsertTuple(tlist1.next());
            System.out.print(aa);
            topt.objectTable.add(new ObjectTableItem(classid,tupid,aa[0],aa[1]));


            biPointerT.biPointerTable.add(new BiPointerTableItem(bedeputyid1,idlist1.next(),classid,tupid));

        }

        while(tlist2.hasNext()&&idlist2.hasNext()){

            topt.maxTupleId++;
            int tupid = topt.maxTupleId;
            int [] aa = InsertTuple(tlist2.next());
            topt.objectTable.add(new ObjectTableItem(classid,tupid,aa[0],aa[1]));


            biPointerT.biPointerTable.add(new BiPointerTableItem(bedeputyid2,idlist2.next(),classid,tupid));

        }
    }


    //SELECT popSinger -> singer.nation  FROM popSinger WHERE singerName = "JayZhou";
    //7,2,popSinger,singer,nation,popSinger,singerName,=,"JayZhou"
    //0 1 2         3      4      5         6          7  8
    private TupleList InDirectSelect(String[] p){
        TupleList tpl= new TupleList();
        String classname = p[3];
        String attrname = p[4];
        String crossname = p[2];
        String[] attrtype = new String[1];
        String[] con =new String[3];
        con[0] = p[6];
        con[1] = p[7];
        con[2] = p[8];

        int classid = 0;
        int crossid = 0;
        String crossattrtype = null;
        int crossattrid = 0;
        for(ClassTableItem item : classt.classTable){
            if(item.classname.equals(classname)){
                classid = item.classid;
                if(attrname.equals(item.attrname))
                    attrtype[0]=item.attrtype;
            }
            if(item.classname.equals(crossname)){
                crossid = item.classid;
                if(item.attrname.equals(con[0])) {
                    crossattrtype = item.attrtype;
                    crossattrid = item.attrid;
                }
            }
        }

        for(ObjectTableItem item1:topt.objectTable){
            if(item1.classid == crossid){
                Tuple tuple = GetTuple(item1.blockid,item1.offset);
                if(Condition(crossattrtype,tuple,crossattrid,con[2])){
                    for(BiPointerTableItem item3: biPointerT.biPointerTable){
                        if(item1.tupleid == item3.objectid&&item3.deputyid == classid){
                            for(ObjectTableItem item2: topt.objectTable){
                                if(item2.tupleid == item3.deputyobjectid){
                                    Tuple ituple = GetTuple(item2.blockid,item2.offset);
                                    tpl.addTuple(ituple);
                                }
                            }
                        }
                    }
                }
            }
        }
        String[] name = new String[1];
        name[0] = attrname;
        int[] id = new int[1];
        id[0] = 0;
        PrintSelectResult(tpl,name,id,attrtype);
        return tpl;


    }

    //UPDATE Song SET type = ‘jazz’WHERE songId = 100;
    //OPT_CREATE_UPDATE，Song，type，“jazz”，songId，=，100
    //0                  1     2      3       4    5   6
    private void Update(String[] p){
        String classname = p[1];
        String attrname = p[2];//更新属性名
        String cattrname = p[4];//条件属性名

        //初值
        int classid = 0;
        int attrid = 0;
        String attrtype = null;
        int cattrid = 0;
        String cattrtype = null;
        int count =0;
        ObjectTableItem obj = new ObjectTableItem();
        for(ClassTableItem item :classt.classTable){
            if (item.classname.equals(classname)){ //类名匹配
                classid = item.classid;
                count=count+1;
            }
        }
        for(ClassTableItem item1 :classt.classTable){
            if (item1.classid==classid&&item1.attrname.equals(attrname)){ //类id、更新属性名匹配
                attrtype = item1.attrtype;
                attrid = item1.attrid;
            }
        }
        for(ClassTableItem item2 :classt.classTable){
            if (item2.classid==classid&&item2.attrname.equals(cattrname)){ //类id、条件属性名匹配
                cattrtype = item2.attrtype;
                cattrid = item2.attrid;
            }
        }
        for(ObjectTableItem item3:topt.objectTable){ //元组
            if(item3.classid == classid){
                Tuple tuple = GetTuple(item3.blockid,item3.offset);
                if(Condition(cattrtype,tuple,cattrid,p[6])){ //条件值
                    obj = UpdatebyID(count,p[2],item3.classid,item3.tupleid,attrid,p[3].replace("\"","")); //更新值
                    System.out.println(obj);

                }
            }
        }
        if (obj!=null){
            topt.objectTable.add(obj);
        }

    }


    private ObjectTableItem UpdatebyID(int namevalue,String attrvalue,int classid, int tupleid,int attrid,String value){
        ObjectTableItem obj = new ObjectTableItem();
        for(ObjectTableItem item: topt.objectTable){
            if(item.tupleid ==tupleid){//元组id匹配
                Tuple tuple = GetTuple(item.blockid,item.offset);
                tuple.tuple[attrid] = value;
                UpateTuple(tuple,item.blockid,item.offset);
                Tuple tuple1 = GetTuple(item.blockid,item.offset);
                UpateTuple(tuple1,item.blockid,item.offset);
            }
        }

        String attrname = null;
        for(ClassTableItem item2: classt.classTable){
            if (item2.attrid == attrid){//属性id匹配
                attrname = item2.attrname;
                break;
            }
        }

        int ideputyid = 0;
        int ideojid = 0;
        for(BiPointerTableItem item1: biPointerT.biPointerTable) {
            if (item1.objectid == tupleid) {
                //更新前满足     //源对象号匹配 判断代理 biPointer
                for(ClassTableItem item4:classt.classTable){
                    if(item4.classid==item1.deputyid){
                        //代理类id匹配
                        String dattrname = item4.attrname;//代理类属性名
                        int dattrid = item4.attrid;// 代理类属性id
                        for (SwitchingTableItem item5 : switchingT.switchingTable) {//switch规则判断
                            String dswitchrule = null;
                            String dvalue = null;

                            String attrname_t = attrvalue;
                            for (DeputyTableItem item6 : deputyt.deputyTable) {

                                if (item6.deputyrule[0].equals(attrname_t)) {
                                    //更新后不满足，删除代理及bi
                                    DeletebyID(item1.deputyobjectid);
                                } else {
                                    if (item5.attr.equals(attrname) && item5.deputy.equals(dattrname)) {//属性值需要switch
                                        //更新前后满足
                                        dvalue = value;//更新值
                                        if (Integer.parseInt(item5.rule) != 0) { //是代理条件
                                            dswitchrule = item5.rule;//switch规则
                                            dvalue = Integer.toString(Integer.parseInt(dvalue) + Integer.parseInt(dswitchrule));//更新值
                                        }
                                        Updatedeputy(item1.deputyobjectid, dattrid, dvalue);//更新代理类，待定
                                        break;
                                    }
                                }
                            }
                        }

                    }
                }
            }

            else{
                //更新前条件不满足，更新后满足，向代理类插入元组
                int count = namevalue;
                int toptmax = 0;
                Tuple ituple = new Tuple();
                for(ObjectTableItem item01: topt.objectTable){
                    if(item01.tupleid ==tupleid){//元组id匹配 找到要更新的

                        Tuple tuple = GetTuple(item01.blockid,item01.offset);
                        tuple.tuple[attrid] = value;
                        tuple.tupleHeader = count;
                        //classid是原来的代码
                        ituple = tuple;


                    }

                }
                for (DeputyTableItem item10 : deputyt.deputyTable) {
                    if (classid == item10.originid) {
                        //判断代理规则,有代理

                        int[] a = InsertTuple(ituple);
                        topt.maxTupleId++;
                        int deojid = topt.maxTupleId;
                        ideojid = deojid;
                        ideputyid = item10.deputyid;
                        obj = new ObjectTableItem(item10.deputyid, deojid, a[0], a[1]);


                        //topt.objectTable.add(new ObjectTableItem(item10.deputyid, deojid, a[0], a[1]));

                    }
                }

            }


        }
        if (ideputyid!=0){

            biPointerT.biPointerTable.add(new BiPointerTableItem(classid, tupleid, ideputyid, ideojid));

        }

        return obj;
    }


    private void Updatedeputy(int tupleid,int attrid,String value){
        for(ObjectTableItem item: topt.objectTable){
            if(item.tupleid ==tupleid){//元组id匹配
                Tuple tuple = GetTuple(item.blockid,item.offset);
                tuple.tuple[attrid] = value;
                UpateTuple(tuple,item.blockid,item.offset);
                Tuple tuple1 = GetTuple(item.blockid,item.offset);
                UpateTuple(tuple1,item.blockid,item.offset);
            }
        }

        String attrname = null;
        for(ClassTableItem item2: classt.classTable){
            if (item2.attrid == attrid){//属性id匹配
                attrname = item2.attrname;
                break;
            }
        }
        for(BiPointerTableItem item1: biPointerT.biPointerTable) {
            if (item1.objectid == tupleid) {  //源对象号匹配//判断代理 biPointer 更新前满足


                for(ClassTableItem item4:classt.classTable){
                    if(item4.classid==item1.deputyid){
                        //代理类id匹配
                        String dattrname = item4.attrname;//代理类属性名
                        int dattrid = item4.attrid;// 代理类属性id
                        for (SwitchingTableItem item5 : switchingT.switchingTable) {//switch规则判断
                            String dswitchrule = null;
                            String dvalue = null;
                            if (item5.attr.equals(attrname) && item5.deputy.equals(dattrname)) {//属性值需要switch
                                dvalue = value;//更新值
                                if (Integer.parseInt(item5.rule) != 0) {
                                    dswitchrule = item5.rule;//switch规则
                                    dvalue = Integer.toString(Integer.parseInt(dvalue) + Integer.parseInt(dswitchrule));//更新值
                                }
                                Updatedeputy(item1.deputyobjectid, dattrid, dvalue);//待定
                                break;
                            }
                        }
                    }
                }
            }
        }

    }


    private class OandB{
        public List<ObjectTableItem> o= new ArrayList<>();
        public List<BiPointerTableItem> b= new ArrayList<>();
        public OandB(){}
        public OandB(OandB oandB){
            this.o = oandB.o;
            this.b = oandB.b;
        }

        public OandB(List<ObjectTableItem> o, List<BiPointerTableItem> b) {
            this.o = o;
            this.b = b;
        }
    }




    private Tuple GetTuple(int id, int offset) {

        return mem.readTuple(id,offset);
    }

    private int[] InsertTuple(Tuple tuple){
        return mem.writeTuple(tuple);
    }

    private void DeleteTuple(int id, int offset){
        mem.deleteTuple();
        return;
    }

    private void UpateTuple(Tuple tuple,int blockid,int offset){
        mem.UpateTuple(tuple,blockid,offset);
    }

    private void PrintTab(ObjectTable topt,SwitchingTable switchingT,DeputyTable deputyt,BiPointerTable biPointerT,ClassTable classTable) {
        Intent intent = new Intent(context, ShowTable.class);

        Bundle bundle0 = new Bundle();
        bundle0.putSerializable("ObjectTable",topt);
        bundle0.putSerializable("SwitchingTable",switchingT);
        bundle0.putSerializable("DeputyTable",deputyt);
        bundle0.putSerializable("BiPointerTable",biPointerT);
        bundle0.putSerializable("ClassTable",classTable);
        intent.putExtras(bundle0);
        context.startActivity(intent);


    }

    private void PrintSelectResult(TupleList tpl, String[] attrname, int[] attrid, String[] type) {
        Intent intent = new Intent(context, PrintResult.class);
        Bundle bundle = new Bundle();
        bundle.putSerializable("tupleList", tpl);
        bundle.putStringArray("attrname", attrname);
        bundle.putIntArray("attrid", attrid);
        bundle.putStringArray("type", type);
        intent.putExtras(bundle);
        context.startActivity(intent);


    }


    public void PrintTab(){
        PrintTab(topt,switchingT,deputyt,biPointerT,classt);
    }



    public void showmap(){
        Intent intent = new Intent(context, Showmap.class);
        Bundle bundle = new Bundle();
        bundle.putSerializable("tupleList", locationtpl);
        intent.putExtras(bundle);
        context.startActivity(intent);
    }



}

