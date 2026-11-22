package com.gunizi.app;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Compare release tags against installed versionName, not unrelated versionCode schemes. */
final class ReleaseVersion {
    private static final Pattern SEMVER=Pattern.compile(
        "(?i)^v?(\\d+)\\.(\\d+)(?:\\.(\\d+))?(?:[-+].*)?$");
    private ReleaseVersion(){}
    static int compare(String publishedTag,String installedVersion){
        int[] remote=parse(publishedTag),local=parse(installedVersion);
        if(remote==null||local==null)return Integer.MIN_VALUE;
        for(int i=0;i<3;i++){
            if(remote[i]>local[i])return 1;
            if(remote[i]<local[i])return -1;
        }
        return 0;
    }
    private static int[] parse(String value){
        if(value==null)return null;
        Matcher matcher=SEMVER.matcher(value.trim());
        if(!matcher.matches())return null;
        try{
            return new int[]{
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3)==null?"0":matcher.group(3))
            };
        }catch(NumberFormatException ex){return null;}
    }
}