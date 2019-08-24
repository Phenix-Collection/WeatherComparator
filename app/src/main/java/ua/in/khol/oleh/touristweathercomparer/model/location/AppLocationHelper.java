package ua.in.khol.oleh.touristweathercomparer.model.location;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.List;

import io.reactivex.Observable;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;
import ua.in.khol.oleh.touristweathercomparer.model.location.pojo.LocationData;
import ua.in.khol.oleh.touristweathercomparer.model.location.pojo.Result;

public class AppLocationHelper implements LocationHelper {
    private LocationManager mLocationManager;
    private LocationListener mLocationListener;

    public AppLocationHelper(Context context) {
        mLocationManager = (LocationManager) context.getApplicationContext()
                .getSystemService(Context.LOCATION_SERVICE);
    }


    public Observable<Location> getSingleLocation(int accuracy, int power) {

        return Observable.create((ObservableOnSubscribe<Location>) emitter -> {
            if (mLocationListener == null) {
                mLocationListener = new LocationListener() {
                    @Override
                    public void onLocationChanged(Location location) {
                        emitter.onNext(location);
                        emitter.onComplete();
                    }

                    @Override
                    public void onStatusChanged(String s, int i, Bundle bundle) {
                    }

                    @Override
                    public void onProviderEnabled(String s) {
                    }

                    @Override
                    public void onProviderDisabled(String s) {
                    }
                };
            }

            update(accuracy, power);
        }).doOnComplete(this::cancel);
    }

    @SuppressLint("MissingPermission")
    private void update(int accuracy, int power) {
        if (mLocationListener != null) {
            mLocationManager.removeUpdates(mLocationListener);
            Criteria criteria = getCriteria(accuracy, power);
            mLocationManager.requestSingleUpdate(criteria, mLocationListener, null);
        }
    }

    private void cancel() {
        if (mLocationListener != null) {
            mLocationManager.removeUpdates(mLocationListener);
            mLocationListener = null;
        }
    }

    private Criteria getCriteria(int accuracy, int power) {
        Criteria criteria = new Criteria();
        int accuracyCriteria;
        switch (accuracy) {
            case 1:
                accuracyCriteria = Criteria.ACCURACY_FINE;
                break;
            case 2:
            default:
                accuracyCriteria = Criteria.ACCURACY_COARSE;
                break;
        }
        criteria.setAccuracy(accuracyCriteria);
        int powerCriteria;
        switch (power) {
            case 1:
            default:
                powerCriteria = Criteria.POWER_LOW;
                break;
            case 2:
                powerCriteria = Criteria.POWER_MEDIUM;
                break;
            case 3:
                powerCriteria = Criteria.POWER_HIGH;
                break;

        }
        criteria.setPowerRequirement(powerCriteria);

        return criteria;
    }

    @Override
    public Observable<String> getLocationName(double latitude, double longitude, String language) {
        Gson gson = new GsonBuilder().create();
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://maps.googleapis.com")
                .addConverterFactory(GsonConverterFactory.create(gson))
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .client(new OkHttpClient.Builder().cache(null).build())
                .build();
        LocationDataService service = retrofit.create(LocationDataService.class);
        Observable<LocationData> observable = service
                .getLocationData(latitude + "," + longitude, language,
                        LocationCityKey.getApiKey());

        return observable
                .map(locationData -> {
                    String name = null;
                    boolean nameFound = false;

                    List<Result> results;
                    if (locationData != null) {
                        results = locationData.getResults();
                        search_locality_name:
                        {
                            for (Result result : results) {
                                for (String type : result.getTypes()) {
                                    if ("locality".compareToIgnoreCase(type) == 0) {
                                        nameFound = true;
                                        name = result
                                                .getAddressComponents().get(0).getShortName();
                                        break search_locality_name;
                                    }
                                }
                            }
                        }


                        if (!nameFound) {
                            for (Result result : results) {
                                for (String type : result.getTypes()) {
                                    if ("administrative_area_level_2"
                                            .compareToIgnoreCase(type) == 0) {
                                        name = result.getFormattedAddress();
                                        break;
                                    }
                                }
                            }

                        }
                    }

                    return name;
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

}
