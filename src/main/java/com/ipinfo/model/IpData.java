package com.ipinfo.model;

import lombok.NoArgsConstructor;
import com.google.gson.annotations.SerializedName;

@NoArgsConstructor
public class IpData {

    private String query;
    private String status;
    private String country;
    private String countryCode;
    private String regionName;
    private String city;
    private String zip;
    private double lat;
    private double lon;
    private String timezone;
    private String isp;
    private String org;

    @SerializedName("as")
    private String as;

    public String getQuery()       { return query; }
    public String getStatus()      { return status; }
    public double getLat()         { return lat; }
    public double getLon()         { return lon; }
    public String getCountry()     { return nullSafe(country); }
    public String getCountryCode() { return nullSafe(countryCode); }
    public String getRegionName()  { return nullSafe(regionName); }
    public String getCity()        { return nullSafe(city); }
    public String getZip()         { return nullSafe(zip); }
    public String getTimezone()    { return nullSafe(timezone); }
    public String getIsp()         { return nullSafe(isp); }
    public String getOrg()         { return nullSafe(org); }
    public String getAs()          { return nullSafe(as); }

    private String nullSafe(String value) {
        return value != null && !value.isBlank() ? value : "N/A";
    }
}
