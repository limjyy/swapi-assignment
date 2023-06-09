package com.assignment.swapi.service;

import com.assignment.swapi.exceptions.ParseSwapiResponseExcpetion;
import com.assignment.swapi.exceptions.SwapiHttpErrorException;
import com.assignment.swapi.models.response.InformationResponse;
import com.assignment.swapi.models.response.ResponseStarship;
import com.assignment.swapi.utils.JsonNodeUtils;
import com.assignment.swapi.utils.RegexUtils;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.core.scheduler.Schedulers;

import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;

@Service
@Slf4j
public class InformationService {
    public static final ResponseStarship DEFAULT_RESPONSE_STARSHIP = new ResponseStarship();
    public static final Long DEFAULT_CREW_NUMBER = 0L;
    public static final Boolean DEFAULT_LEIA_ON_ALDERAAN = false;
    private static final NumberFormat US_NUMBER_FORMAT = NumberFormat.getNumberInstance(Locale.US);
    @Value("${swapi.url.path}")
    private String swapiPath;
    @Value("${swapi.url.people}")
    private String peopleResource;
    @Value("${swapi.url.starships}")
    private String starshipsResource;
    @Value("${swapi.url.planets}")
    private String planetsResource;
    @Value("${swapi.url.people.princess-leia.id}")
    private int leiaId;
    @Value("${swapi.url.people.darth-vader.id}")
    private int darthVaderId;
    @Value("${swapi.url.planets.alderaan.id}")
    private int alderaanId;
    @Value("${swapi.url.starships.death-star.id}")
    private int deathStarId;
    @Autowired
    @Qualifier("swapiWebClient")
    private WebClient webClient;
    @Autowired
    private RegexUtils regexUtils;

    public Mono<InformationResponse> getInformation() {

        Mono<ResponseStarship> responseStarship = getStarshipUrlOfDarthVader()
                .flatMap(this::getStarShipInformationFromUrl)
                .doOnError(e -> log.error("Error occurred obtaining starship of Darth Vader", e))
                .onErrorReturn(DEFAULT_RESPONSE_STARSHIP);
        Mono<Long> crewNumber = getCrewOnDeathStar()
                .doOnError(e -> log.error("Error occurred obtaining crew numbers of Death Star", e))
                .onErrorReturn(DEFAULT_CREW_NUMBER);
        Mono<Boolean> isLeiaOnAlderaan = isLeiaOnAlderaan()
                .doOnError(e -> log.error("Error occurred checking if Leia is on Alderaan", e))
                .onErrorReturn(DEFAULT_LEIA_ON_ALDERAAN);


        return
                Mono.zip(responseStarship, crewNumber, isLeiaOnAlderaan)
                        .subscribeOn(Schedulers.parallel())
                        .map(data ->
                                new InformationResponse(data.getT1(), data.getT2(), data.getT3()));
    }


    public Mono<String> getStarshipUrlOfDarthVader() {

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(swapiPath)
                        .build(peopleResource, darthVaderId))
                .retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse ->
                        Mono.error(new SwapiHttpErrorException(clientResponse.logPrefix(), clientResponse.statusCode())))
                .bodyToMono(JsonNode.class)
                .log(String.valueOf(InformationService.class), Level.FINE, true, SignalType.ON_NEXT)
                .map(jsonNode -> JsonNodeUtils.getOptionalArrayNodeFromJsonNode(jsonNode, "starships")
                        .map(array -> array.get(0))
                        .map(JsonNode::asText)
                )
                .flatMap(Mono::justOrEmpty)
                .filter(StringUtils::isNotBlank)
                .switchIfEmpty(Mono.error(new ParseSwapiResponseExcpetion("No valid starship URL found for darth vader: " + darthVaderId)));

    }

    public Mono<ResponseStarship> getStarShipInformationFromUrl(String urlPath) {

        Optional<Integer> starshipIdOptional = Optional.ofNullable(urlPath)
                .map(regexUtils::extractStarShipIdFromUrl);

        if (starshipIdOptional.isEmpty()) {
            return Mono.error(new ParseSwapiResponseExcpetion("Invalid Starship URL. Does not contain valid starship ID."));
        }

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(swapiPath)
                        .build(starshipsResource, starshipIdOptional.get())
                )
                .retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse ->
                        Mono.error(new SwapiHttpErrorException(clientResponse.logPrefix(), clientResponse.statusCode())))
                .bodyToMono(ResponseStarship.class);
    }

    public Mono<Long> getCrewOnDeathStar() {


        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(swapiPath)
                        .build(starshipsResource, deathStarId))
                .retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse ->
                        Mono.error(new SwapiHttpErrorException(clientResponse.logPrefix(), clientResponse.statusCode())))
                .bodyToMono(JsonNode.class)
                .log(String.valueOf(InformationService.class), Level.FINE, true, SignalType.ON_NEXT)
                .map(jsonNode -> Optional.ofNullable(jsonNode)
                        .map(i -> i.get("crew"))
                        .filter(JsonNode::isTextual)
                        .map(JsonNode::asText)
                        .filter(StringUtils::isNotBlank)
                        .map(crewNumberString -> {
                            try {
                                return US_NUMBER_FORMAT.parse(crewNumberString);
                            } catch (ParseException e) {
                                log.error("Invalid crew number: {}", crewNumberString);
                                return null;
                            }
                        }))
                .flatMap(Mono::justOrEmpty)
                .switchIfEmpty(Mono.error(new ParseSwapiResponseExcpetion("No valid crew number found in SWAPI response")))
                .map(Number::longValue);

    }

    public Mono<Boolean> isLeiaOnAlderaan() {

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(swapiPath)
                        .build(planetsResource, alderaanId))
                .retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse ->
                        Mono.error(new SwapiHttpErrorException(clientResponse.logPrefix(), clientResponse.statusCode())))
                .bodyToMono(JsonNode.class)
                .log(String.valueOf(InformationService.class), Level.FINE, true, SignalType.ON_NEXT)
                .map(jsonNode -> JsonNodeUtils.getOptionalArrayNodeFromJsonNode(jsonNode, "residents"))
                .flatMap(Mono::justOrEmpty)
                .switchIfEmpty(Mono.error(new ParseSwapiResponseExcpetion("No residents list found in SWAPI response")))
                .flatMapIterable(arrayNode -> arrayNode)
                .filter(JsonNode::isTextual)
                .map(JsonNode::asText)
                .map(regexUtils::extractPeopleIdFromUrl)
                .filter(Objects::nonNull)
                .hasElement(leiaId);

    }

}
