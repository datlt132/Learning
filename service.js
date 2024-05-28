const errorFactory = require("../errors/error-factory");
const {getPagination, getPagingData, pageEmpty} = require('../utils/page.util')
const { Op} = require("sequelize");
const moment = require('moment')
const ZipUtil = require("../utils/zip.util");
const {MatchStatus} = require("../enums/match-status.type");
const CommonUtil = require('../utils/common.util')
const _ = require("lodash");
const CryptoJS = require("crypto-js");
const PdfPrinter = require('pdfmake');
const fonts = {
    Roboto: {
        normal: 'fonts/Roboto-Regular.ttf',
        bold: 'fonts/Roboto-Medium.ttf',
        italics: 'fonts/Roboto-Italic.ttf',
        bolditalics: 'fonts/Roboto-MediumItalic.ttf'
    }
};

const listTopMatching = async (req, res, next) => {
    try {
        let {
            pageSize, pageNo, examId, sort, overallCompatibility, startOccurrenceDate,
            endOccurrenceDate, region, country, lawEnforcementAgency
        } = req.query;
        const {limit, offset} = getPagination(pageNo, pageSize);
        const {MatchingSample, X3PExam, Metadata, X3PSample, User} = global.sequelizeModels;
        let metaConditions = []
        if (region) {
            metaConditions.push(sequelize.literal(`"region" = '${region}'`))
        }
        if (country) {
            metaConditions.push(sequelize.literal(`"recovery_location" like '%${country}'`))
        }
        if (startOccurrenceDate) {
            let startDate = moment.utc(startOccurrenceDate, 'YYYY-MM-DD').startOf('day')
            metaConditions.push(sequelize.literal(`"occurrence_date" >= '%${startDate}'`))
        }
        if (endOccurrenceDate) {
            let endDate = moment.utc(endOccurrenceDate, 'YYYY-MM-DD').endOf('day')
            metaConditions.push(sequelize.literal(`"occurrence_date" <= '%${endDate}'`))
        }
        let userCondition = []
        if (lawEnforcementAgency) {
            userCondition.push({agency: lawEnforcementAgency})
        }
        let orderConditions = []
        if(sort) {
            orderConditions.push(sequelize.literal(`"x3pSample.metadata.occurrenceDate" ${sort}`))
        }
        if(overallCompatibility) {
            orderConditions.push(['score', overallCompatibility])
        }
        orderConditions.push(['id', "asc"])

        let exam = await X3PExam.findOne({
            attributes: ['userId'],
            where: {id: examId}
        })
        if(!exam || exam?.userId != req.user.id){
            return next(errorFactory.forbidden("Permission denied!"))
        }

        let samples = await X3PSample.findAll({
            attributes: ['id'],
            include: [{
                model: User,
                as: 'user',
                where: {[Op.and]: [...userCondition]}
            }]
        })

        if(!samples){
            return res.status(200).json(pageEmpty())
        }
        let sampleIds = samples.map(sample => sample.id)

        let { count: totalItems, rows: matchingSampleList} = await MatchingSample.findAndCountAll({
            limit,
            offset,
            attributes: ['id', 'examId', 'sampleId', 'score'],
            where: {examId: examId, score: {[Op.gte]: 0.01}},
            include: [{
                model: X3PSample,
                as: 'x3pSample',
                attributes: ['id'],
                where: {id: {[Op.in]: sampleIds}},
                include: [{
                    model: Metadata,
                    as: 'metadata',
                    attributes: ['fileHash', 'occurrenceDate', 'recoveryLocation'],
                    where: {
                        [Op.and]: metaConditions
                    },
                }],
            }],
            order: orderConditions
        });

        let returnData = matchingSampleList.map(matchingSample => {
            return {
                id: matchingSample?.id,
                examId: matchingSample?.examId,
                sampleId: matchingSample?.sampleId,
                fileHash: matchingSample?.x3pSample?.metadata?.fileHash,
                score: matchingSample?.score,
                occurrenceDate: matchingSample?.x3pSample?.metadata?.occurrenceDate,
                recoveryLocation: matchingSample?.x3pSample?.metadata?.recoveryLocation
            }
        })

        return res.send(getPagingData(returnData, pageNo, limit, totalItems));
    } catch (error) {
        console.error(error);
        return next(error);
    }
}

const changeStatus = async (req, res, next) => {
    const {activityId, matchStatus} = req.query
    const {MatchingSample} = global.sequelizeModels;
    let matchingSample = await MatchingSample.findOne({
        where: {id: activityId}
    })
    if (!matchingSample) {
        return next(errorFactory.notFound(`Cannot find activity with id ${activityId}`));
    }
    matchingSample.matchStatus = matchStatus
    matchingSample.lastSeen = new Date()
    await matchingSample.save()
    return res.status(200).json({
        status: 200,
        message: "Change status successfully"
    })
}

const exportX3p = async (req, res, next) => {
    try {
        const matchingSample = await getMatchingSampleToExport(req)
        const x3pFiles = await getX3pFilesInfo(matchingSample)

        let random = Math.floor(Math.random() * 1000000)
        let fileName = `${CommonUtil.dateFormat(new Date())}_${CryptoJS.MD5(
            random + '').toString()}.zip`
        const zipFile = await ZipUtil.zipFilesToFolder(x3pFiles, "export-x3p", null,
            fileName)

        return res.download(zipFile.zipFilePath);
    } catch (error) {
        console.error(error);
        return next(error);
    }
}

const getX3pFilesInfo = async (matchingSample) => {
    const {X3PSampleFile, X3PExamFile} = global.sequelizeModels

    const sampleIds = [], examIds = [];
    matchingSample.forEach(matchingSample => {
        examIds.push(matchingSample.examId)
        if (matchingSample.matchStatus !== MatchStatus.NO_MATCHES) {
            sampleIds.push(matchingSample.sampleId)
        }
    })

    let sampleFiles = await X3PSampleFile.findAll({
        where: {x3pSampleId: {[Op.in]: sampleIds}}
    })
    let x3pFiles = sampleFiles.map(sampleFile => {
        return {
            fileName: `${sampleFile.fileHash}.x3p`,
            filePath: sampleFile.filePath
        }
    })

    let examFiles = await X3PExamFile.findAll({
        where: {x3pExamId: {[Op.in]: examIds}}
    })
    examFiles.forEach(examFile => {
        x3pFiles.push({
            fileName: `${examFile.fileHash}.x3p`,
            filePath: examFile.filePath
        })
    })
    if (x3pFiles?.length <= 0){
        throw errorFactory.internalServerError("No x3p file found")
    }
    return x3pFiles
}

const getMatchingSampleToExport = async (req) => {
    let {activityIds, type, crime, matchStatus, calibre} = req.query;
    const { MatchingSample, X3PExam, Metadata} = global.sequelizeModels;
    let matchingSampleConditions = []
    if (activityIds?.length > 0) {
        matchingSampleConditions.push(sequelize.literal(`"matching_sample"."id" IN ('${activityIds.join("','")}')`))
    }
    if (matchStatus?.length > 0) {
        matchingSampleConditions.push(sequelize.literal(`"match_status" IN ('${matchStatus.join("','")}')`))
    }
    let examConditions = []
    if (type?.length > 0) {
        examConditions.push(sequelize.literal(`"artefact_type" IN ('${type.join("','")}')`))
    }
    let metaConditions = []
    if (crime?.length > 0) {
        metaConditions.push(sequelize.literal(`"crime" IN (${crime.join(",")})`))
    }
    if (calibre?.length > 0) {
        metaConditions.push(sequelize.literal(`"calibre" IN ('${calibre.join("','")}')`))
    }
    return await MatchingSample.findAll({
        attributes: ['id', 'examId', 'sampleId', 'matchStatus'],
        where: {[Op.and]: matchingSampleConditions} ,
        include: [{
            model: X3PExam,
            as: 'x3pExam',
            attributes: ['artefactType'],
            where: {[Op.and]: [{userId: req.user.id}, ...examConditions]},
            include: [{
                model: Metadata,
                as: 'metadata',
                attributes: ['name', 'crime', 'calibre', 'occurrenceDate'],
                where: {[Op.and]: metaConditions},
            }],
        }]
    });
}

const getX3PDetail = async (matchingSample) => {
    const {X3PSample, X3PExam, Metadata} = global.sequelizeModels
    const sampleIds = [], examIds = [];
    matchingSample.forEach(matchingSample => {
        examIds.push(matchingSample.examId)
        if (matchingSample.matchStatus !== MatchStatus.NO_MATCHES) {
            sampleIds.push(matchingSample.sampleId)
        }
    })

    let sampleInfos = await X3PSample.findAll({
        where: {id: {[Op.in]: sampleIds}},
        include: [{
            model: Metadata,
            as: 'metadata'
        }]
    })
    let x3pDetailInfos = sampleInfos.map(sampleInfo => {
        return {
            id: sampleInfo?.id,
            type: 'sample',
            fileHash: sampleInfo?.metadata?.fileHash,
            artefactType: sampleInfo?.artefactType,
            name: sampleInfo?.metadata?.name,
            description: sampleInfo?.metadata?.description,
            crime: sampleInfo?.metadata?.crime,
            recoveryLocation: sampleInfo?.metadata?.recoveryLocation,
            occurrenceDate: sampleInfo?.metadata?.occurrenceDate,
            calibre: sampleInfo?.metadata?.calibre,
            numberOfLandsAndGrooves: sampleInfo?.metadata?.numberOfLandsAndGrooves,
            directionOfLandsAndGrooves: sampleInfo?.metadata?.directionOfLandsAndGrooves,
            riflingManufacturing: sampleInfo?.metadata?.riflingManufacturing,
            manufacturingMaterial: sampleInfo?.metadata?.manufacturingMaterial
        }
    })

    let examInfos = await X3PExam.findAll({
        where: {id: {[Op.in]: examIds}},
        include: [{
            model: Metadata,
            as: 'metadata'
        }]
    })
    examInfos.forEach(examInfo => {
        x3pDetailInfos.push({
            id: examInfo?.id,
            type: 'exam',
            fileHash: examInfo?.metadata?.fileHash,
            artefactType: examInfo?.artefactType,
            name: examInfo?.metadata?.name,
            description: examInfo?.metadata?.description,
            crime: examInfo?.metadata?.crime,
            recoveryLocation: examInfo?.metadata?.recoveryLocation,
            occurrenceDate: examInfo?.metadata?.occurrenceDate,
            calibre: examInfo?.metadata?.calibre,
            numberOfLandsAndGrooves: examInfo?.metadata?.numberOfLandsAndGrooves,
            directionOfLandsAndGrooves: examInfo?.metadata?.directionOfLandsAndGrooves,
            riflingManufacturing: examInfo?.metadata?.riflingManufacturing,
            manufacturingMaterial: examInfo?.metadata?.manufacturingMaterial
        })
    })

    return x3pDetailInfos;
}

const exportPdf = async (req, res, next) => {
    try {
        const matchingSample = await getMatchingSampleToExport(req)
        const x3pDetailInfos = await getX3PDetail(matchingSample)
        let content = [Object.keys(x3pDetailInfos[0]).map(key => _.startCase(key))];
        x3pDetailInfos.forEach(x3pDetailInfo => {
            content.push(Object.values(x3pDetailInfo))
        })

        const printer = new PdfPrinter(fonts);
        let docDefinition = {
            pageSize: 'A2',
            pageOrientation: 'landscape',
            content: [{
                table: {
                    headerRows: 1,
                    body: content
                }
            }]
        };

        let pdfDoc = printer.createPdfKitDocument(docDefinition, {});
        pdfDoc.pipe(res);
        pdfDoc.end();
    } catch (err) {
        console.error(err)
        next(errorFactory.internalServerError(err))
    }

}

module.exports = {
    listTopMatching,
    changeStatus,
    exportX3p,
    exportPdf,
}