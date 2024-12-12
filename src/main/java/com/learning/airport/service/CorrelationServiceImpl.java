package com.learning.airport.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class CorrelationServiceImpl implements CorrelationService {

    public static final double MIN_SCORE = -1L;
    public static final double MAX_SCORE = 1L;

    private final X3pSampleService x3pSampleService;
    private final X3pSampleFileService x3pSampleFileService;
    private final X3pExamService x3pExamService;
    private final X3pExamFileService x3pExamFileService;
    private final MatchingSampleService matchingSampleService;

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
        ArtefactType examArtefactType = x3pExam.getArtefactType();
        int totalMatchingSamples = x3pSampleService.countByStatusAndTypeAndNumberOfGrooves(
                X3pSampleStatus.APPROVED, numberOfGrooves, examArtefactType);
        x3pExam.setTotalMatchingSamples(totalMatchingSamples);
        x3pExamService.save(x3pExam);

        if (numberOfGrooves > 1) {
            findMatchingForMultiGrooves(numberOfGrooves, x3pExam);
        } else if (examArtefactType == ArtefactType.BREECH_FACE) {
            findMatchingForSingleGrooves(numberOfGrooves, x3pExam, this::calculateCorrelationScoreCFFMax);
        } else if (examArtefactType == ArtefactType.BULLET_STRIATION) {
            findMatchingForSingleGrooves(numberOfGrooves, x3pExam, this::calculateCorrelationScoreBySignature);
        }

        x3pExamService.updateStatus(x3pExam.getId(), X3pExamStatus.PROCESSED);
        x3pExamService.updateIsMatchingSamples(List.of(x3pExam.getId()), false);
    }

    private void findMatchingForSingleGrooves(int numberOfGrooves, X3pExam x3pExam,
                                              BiFunction<X3pExamFile, X3pSampleFile, Double> calcScore) throws Exception {
        int limit = 10;
        int offset = 0;
        X3pExamFile examFile = genSignatureForExam(x3pExam).get(0);
        Set<X3pSample> x3pSampleWithSameTypeAndSameGrooves;
        do {
            x3pSampleWithSameTypeAndSameGrooves = x3pSampleService.findByStatusAndTypeAndNumberOfGrooves(
                    X3pSampleStatus.APPROVED,
                    numberOfGrooves, ArtefactType.BREECH_FACE, limit, offset);
            if (x3pSampleWithSameTypeAndSameGrooves.isEmpty()) {
                break;
            }

            for (X3pSample eachSampleBreechFace : x3pSampleWithSameTypeAndSameGrooves) {
                if (matchingSampleService.exists(x3pExam, eachSampleBreechFace)) {
                    continue;
                }

                double score = calcScore.apply(examFile, eachSampleBreechFace.getX3pSampleFiles().get(0));
                MatchingSample matchingSample = MatchingSample.initMatchingSample(examFile.getX3pExam(),
                        eachSampleBreechFace, (float) score);

                matchingSampleService.save(matchingSample);
                x3pExamService.increaseMatchedSamples(x3pExam.getId());
            }

            offset += limit;
        } while (!x3pSampleWithSameTypeAndSameGrooves.isEmpty());
    }

    private List<X3pExamFile> genSignatureForExam(X3pExam x3pExam) throws Exception {
        List<X3pExamFile> x3pExamFiles = x3pExam.getX3pExamFiles();
        for (X3pExamFile eachExamFile : x3pExamFiles) {
            if (eachExamFile.getSignature() != null && eachExamFile.getSignature().length > 0) {
                continue;
            }

            SignatureAndResolution signatureAndResolution = RUtils.genSignatureFromFilePath(eachExamFile.getFilePath());
            if (signatureAndResolution == null) {
                throw new Exception("genSignature for exam files got error FAIL_TO_GEN_SIGNATURE");
            }

            eachExamFile.setSignature(signatureAndResolution.getSignature());
            eachExamFile.setResolution(signatureAndResolution.getResolution());
            x3pExamFileService.saveAndFlush(eachExamFile);
        }
        return x3pExamFiles;
    }

    private void findMatchingForMultiGrooves(int numberOfGrooves, X3pExam x3pExam) throws Exception {
        long numberBreechFaceFile = x3pExam.getX3pExamFiles().stream()
                .filter(examFile -> examFile.getArtefactType() != ArtefactType.BULLET_STRIATION)
                .count();
        if (numberBreechFaceFile > 0) {
            throw new Exception("at least one examOrSample empty is not STRIATION_TYPE. REQUIRE_BULLET_STRIATION_TYPE");
        }
        List<X3pExamFile> x3pExamFileStriationList = genSignatureForExam(x3pExam);

        int limit = 10;
        int offset = 0;
        Set<X3pSample> x3pSampleWithSameTypeAndSameGrooves;
        do {
            x3pSampleWithSameTypeAndSameGrooves = x3pSampleService.findByStatusAndTypeAndNumberOfGrooves(
                    X3pSampleStatus.APPROVED, numberOfGrooves, ArtefactType.BULLET_STRIATION, limit, offset);

            if (x3pSampleWithSameTypeAndSameGrooves.isEmpty()) {
                log.info("findTopMatch: x3pSampleWithSameTypeAndSameGrooves empty. End");
                break;
            }

            for (X3pSample eachSample : x3pSampleWithSameTypeAndSameGrooves) {
                if (matchingSampleService.exists(x3pExam, eachSample)) {
                    continue;
                }

                List<StriationScore> rankedListScore = new ArrayList<>();
                for (X3pSampleFile eachStriationInSample : eachSample.getX3pSampleFiles()) {
                    for (X3pExamFile eachStriationInExam : x3pExamFileStriationList) {
                        double score = calculateCorrelationScoreBySignature(eachStriationInExam, eachStriationInSample);
                        StriationScore striationScore = new StriationScore();
                        striationScore.setExam(eachStriationInExam);
                        striationScore.setSample(eachStriationInSample);
                        striationScore.setScore(score);
                        rankedListScore.add(striationScore);
                    }
                }

                rankedListScore.sort(Comparator.comparingDouble(StriationScore::getScore).reversed());
                List<StriationScore> topScore = rankedListScore.stream()
                        .limit(numberOfGrooves)
                        .toList();
                double average = topScore.stream()
                        .mapToDouble(StriationScore::getScore)
                        .average()
                        .orElse(0.0);

                MatchingSample matchingSample = MatchingSample.initMatchingSample(x3pExam, eachSample, (float) average);

                matchingSampleService.save(matchingSample);
                x3pExamService.increaseMatchedSamples(x3pExam.getId());
            }

            offset += limit;
        } while (!x3pSampleWithSameTypeAndSameGrooves.isEmpty());
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



