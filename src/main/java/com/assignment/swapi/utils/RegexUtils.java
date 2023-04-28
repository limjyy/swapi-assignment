package com.assignment.swapi.utils;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RegexUtils {


    public final Pattern REGEX_SWAPI_PEOPLE_EXTRACT_ID;

    public final Pattern REGEX_SWAPI_STARSHIP_EXTRACT_ID;

    public RegexUtils(@Value("${swapi.url}") String baseUrl,
                      @Value("${swapi.url.starships}") String starshipResourceParam,
                      @Value("${swapi.url.people}") String peopleResourceParam
                      ) {
        REGEX_SWAPI_STARSHIP_EXTRACT_ID = Pattern.compile(baseUrl + "/"
                + starshipResourceParam
                + "/(\\d)+/");
        REGEX_SWAPI_PEOPLE_EXTRACT_ID = Pattern.compile(baseUrl + "/"
                + peopleResourceParam
                + "/(\\d)+/");
    }

    public Integer extractStarShipIdFromUrl(String url) {
        Matcher m = REGEX_SWAPI_STARSHIP_EXTRACT_ID.matcher(url);
        if(m.matches()) {
            String id = m.group(1);
            if (StringUtils.isBlank(id)) {
                return null;
            }
            try {
                return Integer.valueOf(id);
            } catch (NumberFormatException e) {
                // TODO: not happy case where string is not really an integer
                return null;
            }

        }
        return null;
    }

    public Integer extractPeopleIdFromUrl(String url) {
        Matcher m = REGEX_SWAPI_PEOPLE_EXTRACT_ID.matcher(url);
        if(m.matches()) {
            String id = m.group(1);
            if (StringUtils.isBlank(id)) {
                return null;
            }
            try {
                return Integer.valueOf(id);
            } catch (NumberFormatException e) {
                // TODO: not happy case where string is not really an integer
                return null;
            }

        }
        return null;
    }





}