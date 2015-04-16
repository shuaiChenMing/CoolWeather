package com.ming.coolweather.activity;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.ming.coolweather.R;
import com.ming.coolweather.db.CoolWeatherDB;
import com.ming.coolweather.model.City;
import com.ming.coolweather.model.County;
import com.ming.coolweather.model.Province;
import com.ming.coolweather.util.HttpCallbackListener;
import com.ming.coolweather.util.HttpUtil;
import com.ming.coolweather.util.Utility;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by ming on 2015/4/14.
 */
public class ChooseAreaActivity extends Activity {
    public static final int LEVEL_PROVINCE = 0;
    public static final int LEVEL_CITY = 1;
    public static final int LEVEL_COUNTY = 2;

    private ProgressDialog mProgressDialog;
    private TextView titleText;
    private ListView mListView;
    private ArrayAdapter<String> mAdapter;
    private CoolWeatherDB mCoolWeatherDB;
    private List<String> dataList = new ArrayList<String>();

    // 省列表
    private List<Province> provinceList;
    //城市列表
    private List<City> cityList;
    //县列表
    private List<County> countyList;

    //选中的省份
    private Province selectedProvince;
    //选中的城市
    private City selectedCity;
    //当前选中的级别
    private int currentLevel;

    private boolean isFromWeatherActivity;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        isFromWeatherActivity = getIntent().getBooleanExtra("from_weather_activity", false);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

        if (preferences.getBoolean("city_selected", false) && !isFromWeatherActivity) {
            Intent intent = new Intent(this, WeatherActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.choose_area);
        mListView = (ListView) findViewById(R.id.list_view);
        titleText = (TextView) findViewById(R.id.title_text);
        mAdapter = new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,dataList);
        mListView.setAdapter(mAdapter);
        mCoolWeatherDB = CoolWeatherDB.getInstance(this);
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (currentLevel == LEVEL_PROVINCE) {
                    selectedProvince = provinceList.get(position);
                    queryCities();
                }else if (currentLevel == LEVEL_CITY) {
                    selectedCity = cityList.get(position);
                    queryCounties();
                }else if (currentLevel == LEVEL_COUNTY) {
                    String countyCode = countyList.get(position).getCountyCode();
                    Intent intent = new Intent(ChooseAreaActivity.this, WeatherActivity.class);
                    intent.putExtra("county_code", countyCode);
                    startActivity(intent);
                    finish();
                }
            }
        });
        queryProvinces();

    }


    /**
     * 根据传入的代号和类型从服务器上查询省市县数据。
     */

    private void queryFromServer(final String code, final String type) {
        String address;
        if (!TextUtils.isEmpty(code)) {
            address = "http://www.weather.com.cn/data/list3/city" + code + ".xml";
        } else {
            address = "http://www.weather.com.cn/data/list3/city.xml";
        }

        showProgressDialog();
        HttpUtil.sendHttpRequest(address, new HttpCallbackListener() {
            @Override
            public void OnFinish(String response) {
                boolean result = false;
                if ("province".equals(type)) {
                    result = Utility.handleProvincesResponse(mCoolWeatherDB, response);
                } else if ("city".equals(type)) {
                    result = Utility.handleCitiesResponse(mCoolWeatherDB, response, selectedProvince.getId());
                } else if ("county".equals(type)) {
                    result = Utility.handleCountiesResponse(mCoolWeatherDB, response, selectedCity.getId());
                }
                if (result) {
                    //通过runOnUIThread()方法回到主线程处理逻辑
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            closeProgressDialog();
                            if ("province".equals(type)) {
                                queryProvinces();
                            } else if ("city".equals(type)) {
                                queryCities();
                            } else if ("county".equals(type)) {
                                queryCounties();
                            }
                        }
                    });
                }
            }

            @Override
            public void OnError(Exception e) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        closeProgressDialog();
                        Toast.makeText(ChooseAreaActivity.this,
                                "加载失败", Toast.LENGTH_SHORT);
                    }
                });
            }
        });
    }

    /**
     * 查询市内所有的县，优先从数据库查询，
     * 如果没有查询到再去服务器上查询。
     */
    private void queryCounties() {
        Log.d("queryCounties","query counties");
        countyList = mCoolWeatherDB.loadCounties(selectedCity.getId());
        if (countyList.size() > 0){
            dataList.clear();
            for (County county : countyList) {
                dataList.add(county.getCountyName());
            }
            mAdapter.notifyDataSetChanged();
            mListView.setSelection(0);
            titleText.setText(selectedCity.getCityName());
            currentLevel = LEVEL_COUNTY;
        }else{
            queryFromServer(selectedCity.getCityCode(),"county");
        }
    }

    /**
     * 查询选中省内所有的市，优先从数据库查询，
     * 如果没有查询到在去服务器上查询。
     */
    private void queryCities() {
        Log.d("queryCities","query cities");
        cityList = mCoolWeatherDB.loadCities(selectedProvince.getId());
        if (cityList.size() > 0) {
            dataList.clear();
            for (City city : cityList) {
                dataList.add(city.getCityName());
            }
            mAdapter.notifyDataSetChanged();
            mListView.setSelection(0);
            titleText.setText(selectedProvince.getProvinceName());
            currentLevel = LEVEL_CITY;
        } else {
            queryFromServer(selectedProvince.getProvinceCode(),"city");
        }
    }


    /**
     * 查询全国所有的省，优先从数据库查询
     * 如果没有查询到再去服务器上查询
     */
    private void queryProvinces() {
        Log.d("queryProvinces","query province");
        provinceList = mCoolWeatherDB.loadProvinces();
        if (provinceList.size() > 0) {
            dataList.clear();
            for (Province province : provinceList) {
                dataList.add(province.getProvinceName());
            }
            mAdapter.notifyDataSetChanged();
            titleText.setText("中国");
            currentLevel = LEVEL_PROVINCE;
        } else {
            queryFromServer(null, "province");
        }
    }

    /**
     * 关闭进度对话框
     */
    private void closeProgressDialog() {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
        }
    }


    /**
     * 显示进度对话框
     */
    private void showProgressDialog() {
        if (mProgressDialog == null) {
            mProgressDialog = new ProgressDialog(this);
            mProgressDialog.setMessage("正在加载...");
            mProgressDialog.setCanceledOnTouchOutside(false);
        }
        mProgressDialog.show();
    }

    @Override
    public void onBackPressed() {
        if (currentLevel == LEVEL_COUNTY) {
            queryCities();
        }else if (currentLevel == LEVEL_CITY) {
            queryProvinces();
        }else {
            if (isFromWeatherActivity) {
                Intent intent = new Intent(this, WeatherActivity.class);
                startActivity(intent);
            }
            finish();
        }
    }
}
