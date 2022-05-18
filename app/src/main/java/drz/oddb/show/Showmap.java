package drz.oddb.show;


import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.graphics.Color;
import android.os.Bundle;


import com.amap.api.maps2d.AMap;

import com.amap.api.maps2d.MapView;
import com.amap.api.maps2d.model.LatLng;

import com.amap.api.maps2d.model.PolylineOptions;

import java.util.ArrayList;
import java.util.List;

import drz.oddb.Memory.TupleList;
import drz.oddb.R;

public class Showmap extends AppCompatActivity{

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);//设置对应的XML布局文件
        MapView mapView = (MapView) findViewById(R.id.map);
        mapView.onCreate(savedInstanceState);// 此方法必须重写
        AMap aMap = mapView.getMap();
        Intent intent = getIntent();
        Bundle bundle = intent.getExtras();
        List<LatLng> latLngs = new ArrayList<LatLng>();
        //存数据时要加个人名
        TupleList ll = (TupleList) bundle.getSerializable("tupleList");
        latLngs=getlacation(ll);
        aMap.addPolyline(new PolylineOptions().
                addAll(latLngs).width(10).color(Color.argb(255, 73, 142, 249)));

    }
    public List<LatLng> getlacation(TupleList tpl){
        int tabCol  = 3;
        int tabH = tpl.tuplenum;
        int r;
        int c;
        String stemp;
        Object oj;
        float[][] mylocation = new float[tabH][];
        List<LatLng> latLngs = new ArrayList<LatLng>();
        for(r = 0;r < tabH;r++){
            mylocation[r] = new float[2];
            for(c = 1;c < tabCol;c++){
                oj = tpl.tuplelist.get(r).tuple[c];
                stemp = oj.toString();
                float b=Float.valueOf(stemp);
                mylocation[r][c-1]=b;
            }
        }
        for (int i=0; i<tabH;i++){
            latLngs.add(new LatLng(mylocation[i][0],mylocation[i][1]));
        }
        return latLngs;
    }
}
