package com.learning.airport.service;

import jakarta.persistence.PersistenceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CorrelationServiceImpl implements CorrelationService {

    public static final double MIN_SCORE = -1L;
    public static final double MAX_SCORE = 1L;

    @Autowired
    private X3pSampleService x3pSampleService;

    @Autowired
    private X3pSampleFileService x3pSampleFileService;

    @Autowired
    private X3pExamService x3pExamService;

    @Autowired
    private X3pExamFileService x3pExamFileService;

    @Autowired
    private MatchingSampleService matchingSampleService;

    private void findTopMatchForExam(BigInteger x3pExamId) throws Exception {
        X3pExam x3pExam = x3pExamService.findById(x3pExamId);
        if (x3pExam == null) {
            return;
        }

        List<X3pExamFile> x3pExamFiles = x3pExam.getX3pExamFiles();
        if (x3pExamFiles.isEmpty()) {
            throw new Exception("x3pExamFiles empty. REQUIRE_X3P_FILE");
        }

        final int numberOfGrooves = x3pExamFiles.size();
        int totalMatchingSamples = x3pSampleService.countByStatusAndTypeAndNumberOfGrooves(
            X3pSampleStatus.APPROVED,
            numberOfGrooves, x3pExam.getArtefactType()
        );
        x3pExam.setTotalMatchingSamples(totalMatchingSamples);
        x3pExamService.save(x3pExam);

        boolean success = false;
        if (x3pExamFiles.size() > 1) {
            for (X3pExamFile examFile : x3pExamFiles) {
                if (!examFile.getArtefactType().equals(ArtefactType.BULLET_STRIATION)) {
                    throw new Exception("at least one examOrSample empty is not STRIATION_TYPE. REQUIRE_BULLET_STRIATION_TYPE");
                }
            }

            int limit = 10, offset = 0;
            Set<X3pSample> x3pSampleWithSameTypeAndSameGrooves;
            do {
                x3pSampleWithSameTypeAndSameGrooves = x3pSampleService.findByStatusAndTypeAndNumberOfGrooves(
                    X3pSampleStatus.APPROVED,
                    numberOfGrooves, ArtefactType.BULLET_STRIATION, limit, offset);

                if (x3pSampleWithSameTypeAndSameGrooves.isEmpty()) {
                    break;
                }

                log.info("Start calculate score for multi striation files");
                List<X3pExamFile> x3pExamFileStriationList = x3pExam.getX3pExamFiles();

                for (X3pExamFile eachExamFileStriation : x3pExamFileStriationList) {
                    if (eachExamFileStriation.getSignature() != null
                        && eachExamFileStriation.getSignature().length > 0) {
                        continue;
                    }

                    SignatureAndResolution signatureAndResolution = RUtils.genSignatureFromFilePath(
                        eachExamFileStriation.getFilePath());
                    if (signatureAndResolution == null) {
                        throw new Exception(
                            "genSignature for exam files got error FAIL_TO_GEN_SIGNATURE");
                    }

                    eachExamFileStriation.setSignature(signatureAndResolution.getSignature());
                    eachExamFileStriation.setResolution(signatureAndResolution.getResolution());
                    x3pExamFileService.saveAndFlush(eachExamFileStriation);
                }

                for (X3pSample eachSample : x3pSampleWithSameTypeAndSameGrooves) {
                    if (matchingSampleService.exists(x3pExam, eachSample)) {
                        continue;
                    }

                    List<StriationScore> rankedListScore = new ArrayList<>();
                    double average = 0.0;
                    for (X3pSampleFile eachStriationInSample : eachSample.getX3pSampleFiles()) {
                        for (X3pExamFile eachStriationInExam : x3pExamFileStriationList) {
                            double score = calculateCorrelationScoreBySignature(
                                eachStriationInExam, eachStriationInSample);

                            StriationScore striationScore = new StriationScore();
                            striationScore.setExam(eachStriationInExam);
                            striationScore.setSample(eachStriationInSample);
                            striationScore.setScore(score);
                            rankedListScore.add(striationScore);
                        }
                    }

                    rankedListScore.sort(
                        Comparator.comparingDouble(StriationScore::getScore).reversed());
                    List<StriationScore> topScore = rankedListScore.stream()
                        .limit(numberOfGrooves)
                        .collect(Collectors.toList());
                    average = topScore.stream()
                        .mapToDouble(StriationScore::getScore)
                        .average()
                        .orElse(0.0);

                    MatchingSample matchingSample = MatchingSample.builder()
                        .x3pExam(x3pExam)
                        .x3pSample(eachSample)
                        .score((float) average)
                        .matchStatus(MatchStatus.NO_MATCHES)
                        .createdAt(Instant.now())
                        .build();
                    matchingSampleService.save(matchingSample);

                    x3pExamService.increaseMatchedSamples(x3pExam.getId());
                }

                offset += limit;
            } while (!x3pSampleWithSameTypeAndSameGrooves.isEmpty());

            success = true;
        } else {
            if (x3pExam.getArtefactType().equals(ArtefactType.BREECH_FACE)) {
                int limit = 10, offset = 0;
                Set<X3pSample> x3pSampleWithSameTypeAndSameGrooves;
                do {
                    x3pSampleWithSameTypeAndSameGrooves = x3pSampleService.findByStatusAndTypeAndNumberOfGrooves(
                        X3pSampleStatus.APPROVED,
                        numberOfGrooves, ArtefactType.BREECH_FACE, limit, offset);
                    if (x3pSampleWithSameTypeAndSameGrooves.isEmpty()) {
                        break;
                    }

                    X3pExamFile examFile = x3pExam.getX3pExamFiles().get(0);

                    if (examFile.getSignature() == null || examFile.getSignature().length < 1) {
                        SignatureAndResolution sign = RUtils.genSignatureFromFilePath(
                            examFile.getFilePath());
                        if (sign == null) {
                            throw new Exception("genSignature for exam files got error FAIL_TO_GEN_SIGNATURE");
                        }

                        examFile.setSignature(sign.getSignature());
                        examFile.setResolution(sign.getResolution());
                        x3pExamFileService.saveAndFlush(examFile);
                    }

                    for (X3pSample eachSampleBreechFace : x3pSampleWithSameTypeAndSameGrooves) {
                        if (matchingSampleService.exists(x3pExam, eachSampleBreechFace)) {
                            continue;
                        }

                        double score = calculateCorrelationScoreCFFMax(examFile,
                            eachSampleBreechFace.getX3pSampleFiles().get(0));

                        MatchingSample matchingSample = MatchingSample.builder()
                            .x3pExam(examFile.getX3pExam())
                            .x3pSample(
                                eachSampleBreechFace.getX3pSampleFiles().get(0).getX3pSample())
                            .score((float) score)
                            .matchStatus(MatchStatus.NO_MATCHES)
                            .createdAt(Instant.now())
                            .build();

                        matchingSampleService.save(matchingSample);

                        x3pExamService.increaseMatchedSamples(x3pExam.getId());
                    }

                    offset += limit;
                } while (!x3pSampleWithSameTypeAndSameGrooves.isEmpty());

                success = true;
            } else if (x3pExam.getArtefactType().equals(ArtefactType.BULLET_STRIATION)) {
                int limit = 10, offset = 0;
                Set<X3pSample> x3pSampleWithSameTypeAndSameGrooves;
                do {
                    x3pSampleWithSameTypeAndSameGrooves = x3pSampleService.findByStatusAndTypeAndNumberOfGrooves(
                        X3pSampleStatus.APPROVED,
                        numberOfGrooves, ArtefactType.BULLET_STRIATION, limit, offset);
                    if (x3pSampleWithSameTypeAndSameGrooves.isEmpty()) {
                        break;
                    }

                    X3pExamFile examFile = x3pExam.getX3pExamFiles().get(0);

                    if (examFile.getSignature() == null || examFile.getSignature().length < 1) {
                        SignatureAndResolution sign = RUtils.genSignatureFromFilePath(
                            examFile.getFilePath());
                        if (sign == null) {
                            throw new Exception(
                                "genSignature for exam files got error FAIL_TO_GEN_SIGNATURE");
                        }

                        examFile.setSignature(sign.getSignature());
                        examFile.setResolution(sign.getResolution());
                        x3pExamFileService.saveAndFlush(examFile);
                    }

                    for (X3pSample eachSampleStriation : x3pSampleWithSameTypeAndSameGrooves) {
                        if (matchingSampleService.exists(x3pExam, eachSampleStriation)) {
                            continue;
                        }

                        double score = calculateCorrelationScoreBySignature(examFile,
                            eachSampleStriation.getX3pSampleFiles().get(0));

                        MatchingSample matchingSample = MatchingSample.builder()
                            .x3pExam(examFile.getX3pExam())
                            .x3pSample(
                                eachSampleStriation.getX3pSampleFiles().get(0).getX3pSample())
                            .score((float) score)
                            .matchStatus(MatchStatus.NO_MATCHES)
                            .createdAt(Instant.now())
                            .build();

                        matchingSampleService.save(matchingSample);

                        x3pExamService.increaseMatchedSamples(x3pExam.getId());
                    }

                    offset += limit;
                } while (!x3pSampleWithSameTypeAndSameGrooves.isEmpty());

                success = true;
            }
        }

        if (success) {
            x3pExamService.updateStatus(x3pExam.getId(), X3pExamStatus.PROCESSED);
        }

        x3pExamService.updateIsMatchingSamples(List.of(x3pExam.getId()), false);
    }

    private double calculateCorrelationScoreBySignature(X3pExamFile exam, X3pSampleFile sample) {
        double score = 0.5f;
        return score < MIN_SCORE || score > MAX_SCORE ? 0 : score;
    }

    private double calculateCorrelationScoreCFFMax(X3pExamFile exam, X3pSampleFile sample) {
        double score = 0.5f;
        return score < MIN_SCORE || score > MAX_SCORE ? 0 : score;
    }
}



