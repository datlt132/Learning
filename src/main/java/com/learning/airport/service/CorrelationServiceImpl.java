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

    @Override
    public void generateSignature(List<BigInteger> sampleIds) {
        log.info("getSignature: ...");

        if (sampleIds == null || sampleIds.isEmpty()) {
            log.error("getSignature: ... missing files");
            return;
        }

        try {
            sampleIds.forEach(sampleId -> {
                try {
                    genSignatureForSampleFile(sampleId);
                } catch (Exception e) {
                    log.error("getSignature: ... x3pSample #" + sampleId + ": error: ", e);
                }
            });

            log.info("======== getSignature end");
        } catch (Exception e) {
            log.error("getSignature: ... error", e);
        }
    }

    @Override
    public void matchSample(List<BigInteger> examIds) {
        log.info("findTopMatch: ...");

        if (examIds == null || examIds.isEmpty()) {
            log.error("findTopMatch: ... missing files");
            return;
        }

        try {
            examIds.forEach(examId -> {
                try {
                    findTopMatchForExam(examId);
                } catch (Exception e) {
                    log.error("findTopMatch: ... x3pExam #" + examId + ": error: ", e);
                }
            });

            log.info("======== findTopMatch end");
        } catch (Exception e) {
            log.info("findTopMatch: ... error: ", e);
        }
    }

    private void genSignatureForSampleFile(BigInteger id) throws Exception {
        try {
            X3pSample sample = x3pSampleService.findById(id);
            if (sample == null) {
                log.info("X3pSample #" + id + " Not found");
                return;
            }

            // gen signature
            BigInteger idKey = sample.getId();
            List<X3pSampleFile> inputFilesList = sample.getX3pSampleFiles();
            long numberFile = inputFilesList.size();
            if (x3pSampleService.findByIdAndStatus(idKey, X3pSampleStatus.APPROVED) == null) {
                log.info(
                    "=== addX3P:... Sample with idKey: " + idKey + " is not approved, skip");
                return;
            }

            if (numberFile == 0) {
                log.info("=== addX3P:... error: REQUIRE_X3P_FILE");
                throw new Exception("error: REQUIRE_X3P_FILE");
            }

            log.info(
                "=== addX3P:... adding Sample with idKey: " + idKey
                    + " generating signature");

            if (numberFile > 1) {
                boolean typeStriation = inputFilesList.stream()
                    .allMatch(x3P -> x3P.getArtefactType() == ArtefactType.BULLET_STRIATION);
                if (!typeStriation) {
                    log.info("=== addX3P:... error: REQUIRE_BULLET_STRIATION_TYPE");
                    throw new Exception("error: REQUIRE_BULLET_STRIATION_TYPE");
                }
            }

            log.info("=== addX3P:... numberFile: " + numberFile);

            /*
             *  Push path to R server to generate signature for single x3p file
             *  the R response for matcher to save signature (a string) and scope
             *  This process will take a long time, so it must be in queue for not missing another request
             */
            boolean hasError = false;
            try {
                for (X3pSampleFile sampleFile : inputFilesList) {
                    log.info(
                        "=== addX3P:... adding file %s (id #%d)".formatted(sampleFile.getFilePath(),
                            sampleFile.getId()));

                    if (sampleFile.getSignature() != null
                        && sampleFile.getSignature().length > 0) {
                        log.info(
                            "=== addX3P:... already generated signature for file %s (id #%d) (type #%s)".formatted(
                                sampleFile.getFilePath(), sampleFile.getId(),
                                sampleFile.getArtefactType()));
                        continue; // already has signature
                    }

                    SignatureAndResolution signatureAndResolution = RUtils.genSignatureFromFilePath(
                        sampleFile.getFilePath());
                    log.info(
                        "=== addX3P:... Generate signature for file %s (id #%d) (type #%s)".formatted(
                            sampleFile.getFilePath(), sampleFile.getId(),
                            sampleFile.getArtefactType()));
                    if (signatureAndResolution == null) {
                        System.err.println(
                            "=== addX3P:... error: got empty signatureAndResolution from to R (FAIL_TO_GEN_SIGNATURE)");
                        hasError = true;
                        continue;
                    }

                    if (signatureAndResolution.getSignature() == null
                        || signatureAndResolution.getSignature().length < 1) {
                        System.err.println(
                            "=== addX3P:... error: got empty Signature from to R");
                        hasError = true;
                        continue;
                    }

                    // save signature and resolution for sample file
                    sampleFile.setSignature(signatureAndResolution.getSignature());
                    sampleFile.setResolution(signatureAndResolution.getResolution());
                    x3pSampleFileService.saveAndFlush(sampleFile);
                }
            } catch (PersistenceException e) {
                log.error("=== addX3P:... error: PersistenceException: " + e.getMessage());
                throw new Exception("error: PersistenceException");
            } catch (Exception e) {
                log.error("=== addX3P:... error: unknown Exception: " + e.getMessage());
                throw new Exception("error: unknown Exception");
            }

            /* update syncAt for sample if totally has no error */
            if (!hasError) {
                sample.setSyncedAt(Instant.now());
            }

            // always update status not processing
            sample.setIsSynchronizing(false);
            x3pSampleService.saveAndFlush(sample);
        } catch (Exception e) {
            throw new Exception("genSignatureForSampleFile error");
        }
    }

    private void findTopMatchForExam(BigInteger x3pExamId) throws Exception {
        log.info("findTopMatch: x3pExam (" + x3pExamId + "): ... ");
        X3pExam x3pExam = x3pExamService.findById(x3pExamId);
        if (x3pExam == null) {
            log.info("findTopMatch: x3pExam not found. End");
            return;
        }

        List<X3pExamFile> x3pExamFiles = x3pExam.getX3pExamFiles();
        if (x3pExamFiles.isEmpty()) {
            log.info("findTopMatch: x3pExamFiles empty. End");
            throw new Exception("x3pExamFiles empty. REQUIRE_X3P_FILE");
        }

        final int numberOfGrooves = x3pExamFiles.size();
        // update total matching samples for exam
        int totalMatchingSamples = x3pSampleService.countByStatusAndTypeAndNumberOfGrooves(
            X3pSampleStatus.APPROVED,
            numberOfGrooves, x3pExam.getArtefactType()
        );
        x3pExam.setTotalMatchingSamples(totalMatchingSamples);
        x3pExamService.save(x3pExam);

        boolean success = false;
        /*
         * handle for BULLET_STRIATION that exactly has number of x3pExamFiles > 1
         */
        if (x3pExamFiles.size() > 1) {
            // this case must be multi file striation
            for (X3pExamFile examFile : x3pExamFiles) {
                if (!examFile.getArtefactType().equals(ArtefactType.BULLET_STRIATION)) {
                    log.info(
                        "findTopMatch: at least one examOrSample empty is not STRIATION_TYPE. End");
                    throw new Exception(
                        "at least one examOrSample empty is not STRIATION_TYPE. REQUIRE_BULLET_STRIATION_TYPE");
                }
            }

            // batch process x3pSampleWithSameTypeAndSameGrooves instead of getting all
            // to avoid over memory heap
            // TODO: extract function...
            int limit = 10, offset = 0;
            Set<X3pSample> x3pSampleWithSameTypeAndSameGrooves;
            do {
                x3pSampleWithSameTypeAndSameGrooves = x3pSampleService.findByStatusAndTypeAndNumberOfGrooves(
                    X3pSampleStatus.APPROVED,
                    numberOfGrooves, ArtefactType.BULLET_STRIATION, limit, offset);

                if (x3pSampleWithSameTypeAndSameGrooves.isEmpty()) {
                    log.info("findTopMatch: x3pSampleWithSameTypeAndSameGrooves empty. End");
                    break;
                }

                log.info("Start calculate score for multi striation files");
                List<X3pExamFile> x3pExamFileStriationList = x3pExam.getX3pExamFiles();

                // generate signature for exam file if not has signature before
                for (X3pExamFile eachExamFileStriation : x3pExamFileStriationList) {
                    if (eachExamFileStriation.getSignature() != null
                        && eachExamFileStriation.getSignature().length > 0) {
                        continue; // already has signature
                    }

                    SignatureAndResolution signatureAndResolution = RUtils.genSignatureFromFilePath(
                        eachExamFileStriation.getFilePath());
                    if (signatureAndResolution == null) {
                        log.info(
                            "findTopMatch: genSignature for exam files got error FAIL_TO_GEN_SIGNATURE. End");
                        throw new Exception(
                            "genSignature for exam files got error FAIL_TO_GEN_SIGNATURE");
                    }

                    // save
                    log.info("findTopMatch: saving exam data to DB...");
                    eachExamFileStriation.setSignature(signatureAndResolution.getSignature());
                    eachExamFileStriation.setResolution(signatureAndResolution.getResolution());
                    x3pExamFileService.saveAndFlush(eachExamFileStriation);
                }

                // calculating score with each sample
                // this list will save the highest score base on num of grooves
                for (X3pSample eachSample : x3pSampleWithSameTypeAndSameGrooves) {
                    /* one sample */
                    // skip samples that matched with the exam before
                    if (matchingSampleService.exists(x3pExam, eachSample)) {
                        continue;
                    }

                    List<StriationScore> rankedListScore = new ArrayList<>();
                    double average = 0.0;
                    for (X3pSampleFile eachStriationInSample : eachSample.getX3pSampleFiles()) {
                        /* one sampleFile */
                        for (X3pExamFile eachStriationInExam : x3pExamFileStriationList) {
                            /* one examFile */
                            double score = calculateCorrelationScoreBySignature(
                                eachStriationInExam, eachStriationInSample);
                            log.info("file : " + eachStriationInExam.getFilePath()
                                + "compared with : " + eachStriationInSample.getFilePath()
                                + " score = " + score);

                            // save rankedListScore
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

                    log.info("findTopMatch: x3pExam (" + x3pExamId + "): ... score: " + average);

                    // save score to for a pair
                    MatchingSample matchingSample = MatchingSample.builder()
                        .x3pExam(x3pExam)
                        .x3pSample(eachSample)
                        .score((float) average)
                        .matchStatus(MatchStatus.NO_MATCHES)
                        .createdAt(Instant.now())
                        .build();
                    matchingSampleService.save(matchingSample);

                    // increase matchedSamples for exam
                    x3pExamService.increaseMatchedSamples(x3pExam.getId());
                }

                offset += limit;
            } while (!x3pSampleWithSameTypeAndSameGrooves.isEmpty());

            success = true;
        } else {
            /* handle for BREECH_FACE */
            if (x3pExam.getArtefactType().equals(ArtefactType.BREECH_FACE)) {
                int limit = 10, offset = 0;
                Set<X3pSample> x3pSampleWithSameTypeAndSameGrooves;
                do {
                    x3pSampleWithSameTypeAndSameGrooves = x3pSampleService.findByStatusAndTypeAndNumberOfGrooves(
                        X3pSampleStatus.APPROVED,
                        numberOfGrooves, ArtefactType.BREECH_FACE, limit, offset);
                    if (x3pSampleWithSameTypeAndSameGrooves.isEmpty()) {
                        log.info("findTopMatch: x3pSampleWithSameTypeAndSameGrooves empty. End");
                        break;
                    }

                    X3pExamFile examFile = x3pExam.getX3pExamFiles().get(0);

                    // generate signature for exam file if not has signature before
                    if (examFile.getSignature() == null || examFile.getSignature().length < 1) {
                        SignatureAndResolution sign = RUtils.genSignatureFromFilePath(
                            examFile.getFilePath());
                        if (sign == null) {
                            log.info(
                                "findTopMatch: genSignature for exam files got error FAIL_TO_GEN_SIGNATURE. End");
                            throw new Exception(
                                "genSignature for exam files got error FAIL_TO_GEN_SIGNATURE");
                        }

                        // save signature for exam file
                        examFile.setSignature(sign.getSignature());
                        examFile.setResolution(sign.getResolution());
                        x3pExamFileService.saveAndFlush(examFile);
                    }

                    // calculating score with each sample
                    for (X3pSample eachSampleBreechFace : x3pSampleWithSameTypeAndSameGrooves) {
                        // skip samples that matched with the exam before
                        if (matchingSampleService.exists(x3pExam, eachSampleBreechFace)) {
                            continue;
                        }

                        double score = calculateCorrelationScoreCFFMax(examFile,
                            eachSampleBreechFace.getX3pSampleFiles().get(0));
                        log.info("file : " + examFile.getFilePath() + "compared with : "
                            + eachSampleBreechFace.getX3pSampleFiles().get(0).getFilePath()
                            + " score = " + score);

                        MatchingSample matchingSample = MatchingSample.builder()
                            .x3pExam(examFile.getX3pExam())
                            .x3pSample(
                                eachSampleBreechFace.getX3pSampleFiles().get(0).getX3pSample())
                            .score((float) score)
                            .matchStatus(MatchStatus.NO_MATCHES)
                            .createdAt(Instant.now())
                            .build();

                        // save score to for a pair
                        matchingSampleService.save(matchingSample);

                        // increase matchedSamples for exam
                        x3pExamService.increaseMatchedSamples(x3pExam.getId());
                    }

                    offset += limit;
                } while (!x3pSampleWithSameTypeAndSameGrooves.isEmpty());

                success = true;
            } else if (x3pExam.getArtefactType().equals(ArtefactType.BULLET_STRIATION)) {
                // this case have a restriction that only have 1 striation file
                int limit = 10, offset = 0;
                Set<X3pSample> x3pSampleWithSameTypeAndSameGrooves;
                do {
                    x3pSampleWithSameTypeAndSameGrooves = x3pSampleService.findByStatusAndTypeAndNumberOfGrooves(
                        X3pSampleStatus.APPROVED,
                        numberOfGrooves, ArtefactType.BULLET_STRIATION, limit, offset);
                    if (x3pSampleWithSameTypeAndSameGrooves.isEmpty()) {
                        log.info("findTopMatch: x3pSampleWithSameTypeAndSameGrooves empty. End");
                        break;
                    }

                    X3pExamFile examFile = x3pExam.getX3pExamFiles().get(0);

                    // generate signature for exam file if not has signature before
                    if (examFile.getSignature() == null || examFile.getSignature().length < 1) {
                        SignatureAndResolution sign = RUtils.genSignatureFromFilePath(
                            examFile.getFilePath());
                        if (sign == null) {
                            log.info(
                                "findTopMatch: genSignature for exam files got error FAIL_TO_GEN_SIGNATURE. End");
                            throw new Exception(
                                "genSignature for exam files got error FAIL_TO_GEN_SIGNATURE");
                        }

                        // save signature for exam file
                        examFile.setSignature(sign.getSignature());
                        examFile.setResolution(sign.getResolution());
                        x3pExamFileService.saveAndFlush(examFile);
                    }

                    // calculating score with each sample
                    for (X3pSample eachSampleStriation : x3pSampleWithSameTypeAndSameGrooves) {
                        // skip samples that matched with the exam before
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

                        // save score to for a pair
                        matchingSampleService.save(matchingSample);

                        // increase matchedSamples for exam
                        x3pExamService.increaseMatchedSamples(x3pExam.getId());
                    }

                    offset += limit;
                } while (!x3pSampleWithSameTypeAndSameGrooves.isEmpty());

                success = true;
            }
        }

        /* update x3pExam status if success */
        if (success) {
            // call updateStatus() instead of save() because of previous call
            // increaseMatchedSamples() not update x3pExam instance
            x3pExamService.updateStatus(x3pExam.getId(), X3pExamStatus.PROCESSED);
        }

        // always update status not processing
        x3pExamService.updateIsMatchingSamples(List.of(x3pExam.getId()), false);
    }

    private double calculateCorrelationScoreBySignature() {
        float score = 0.5f;
        log.info("CorrelationService: calculateCorrelationScoreBySignature: got score: " + score);
        return score < MIN_SCORE || score > MAX_SCORE ? 0 : score;
    }

    private double calculateCorrelationScoreCFFMax() {
        float score = 0.5f;
        log.info("CorrelationService: calculateCorrelationScoreBySignature: got score: " + score);
        return score < MIN_SCORE || score > MAX_SCORE ? 0 : score;
    }
}



