const chai = require('chai');
const chaiHttp = require('chai-http');
const sinon = require('sinon');
const expect = chai.expect;
const errorFactory = require("../../errors/error-factory");
const matchingSampleController = require("../../controller/matching-sample.controller")
const {describe, it, beforeEach, afterEach} = require("mocha");
const {pageEmpty, getPagingData} = require("../../utils/page.util");
const CommonUtil = require("../../utils/common.util")
const ZipUtil = require("../../utils/zip.util")
const CryptoJS = require("crypto-js");
chai.use(chaiHttp);


describe('listTopMatching', () => {
    let req, res, next
    let matchingSampleFindAndCountAllStub, x3pExamFindOneStub, x3pSampleFindAllStub

    beforeEach(() => {
        req = {
            query: {
                pageNo: 0,
                pageSize: 10,
                examId: 1,
                sort: 'asc',
                overallCompatibility: 'asc',
                startOccurrenceDate: '2024/04/10',
                endOccurrenceDate: '2024/04/11',
                region: 'Asia',
                country: 'Vietnam',
                lawEnforcementAgency: 'FBI'
            },
            user: {
                id: 7
            }
        }
        res = {
            status: sinon.stub().returnsThis(),
            json: sinon.stub(),
            send: sinon.stub()
        }
        next = sinon.spy()

        global.sequelizeModels = {
            MatchingSample: {
                findAndCountAll() {
                }
            },
            X3PExam: {
                findOne() {
                }
            },
            X3PSample: {
                findAll() {
                }
            }
        }
        matchingSampleFindAndCountAllStub = sinon.stub(global.sequelizeModels.MatchingSample, 'findAndCountAll')
        x3pExamFindOneStub = sinon.stub(global.sequelizeModels.X3PExam, 'findOne')
        x3pSampleFindAllStub = sinon.stub(global.sequelizeModels.X3PSample, 'findAll')
        sinon.stub(errorFactory, 'forbidden')
    })

    afterEach(() => {
        sinon.restore()
    })

    it('should return forbidden if user does not have permission', async () => {
        x3pExamFindOneStub.resolves(null)
        await matchingSampleController.listTopMatching(req, res, next)
        expect(next.calledWith(errorFactory.forbidden("Permission denied!"))).to.be.true
    })

    it('should return empty page if no sample match query params', async () => {
        x3pExamFindOneStub.resolves({userId: 7})
        x3pSampleFindAllStub.resolves(null)
        await matchingSampleController.listTopMatching(req, res, next)
        expect(res.status.calledWith(200)).to.be.true
        expect(res.json.calledWith(pageEmpty())).to.be.true
    })

    it('should return error if database throw exception', async () => {
        let dbError = new Error('Sequelize error')
        x3pExamFindOneStub.rejects(dbError)
        await matchingSampleController.listTopMatching(req, res, next)
        expect(next.calledWith(dbError))
    })

    it('should return page matching sample if query valid', async () => {
        x3pExamFindOneStub.resolves({userId: 7})
        x3pSampleFindAllStub.resolves([{id: 1}])
        matchingSampleFindAndCountAllStub.resolves({
            count: 1,
            rows: [{
                id: 1,
                examId: 1,
                sampleId: 1,
                score: 1,
                x3pSample: {
                    metadata: {
                        fileHash: 'fileHash',
                        occurrenceDate: 'occurrenceDate',
                        recoveryLocation: 'recoveryLocation'
                    }
                }
            }]
        })
        let returnData = [{
            id: 1,
            examId: 1,
            sampleId: 1,
            fileHash: 'fileHash',
            score: 1,
            occurrenceDate: 'occurrenceDate',
            recoveryLocation: 'recoveryLocation'
        }]
        await matchingSampleController.listTopMatching(req, res, next)
        expect(res.send.calledWith(getPagingData(returnData, 0, 10, 1))).to.be.true
    })
}

describe('changeStatus', () => {
    let req, res, next
    let matchingSampleFineOneStub

    beforeEach(() => {
        req = {
            query: {activityId: 1, matchStatus: "matchStatus"},
            user: {id: 1}
        };
        res = {
            status: sinon.stub().returnsThis(),
            json: sinon.stub()
        };
        next = sinon.stub();
        global.sequelizeModels = {
            MatchingSample: {
                findOne() {
                },
            }
        };

        matchingSampleFineOneStub = sinon.stub(global.sequelizeModels.MatchingSample, "findOne");
        sinon.stub(errorFactory, "notFound");
    });

    afterEach(() => {
        sinon.restore();
    });

    it('should return 404 if not found activity', async () => {
        matchingSampleFineOneStub.resolves()

        await matchingSampleController.changeStatus(req, res, next)

        expect(next.calledOnce).to.be.true
        expect(next.calledWith(errorFactory.notFound("Cannot find activity with id 1"))).to.be.true
    })

    it('should return 200 if request valid', async () => {
        matchingSampleFineOneStub.resolves({
            matchStatus: "", lastSeen: null, save() {
            }
        })

        await matchingSampleController.changeStatus(req, res, next)

        expect(res.status.calledWith(200)).to.be.true
        expect(res.json.calledWith({
            status: 200,
            message: "Change status successfully"
        })).to.be.true
    })

})

describe('exportX3p', () => {
    let req, res, next
    let matchingSampleFindAllStub, sampleFileFindAllStub, examFileFindAllStub

    beforeEach(() => {
        req = {
            query: {
                activityIds: [1],
                matchStatus: ["matchStatus"],
                type: ["type"],
                crime: ["crime"],
                calibre: ["calibre"]
            },
            user: {id: 1}
        };
        res = {
            download: sinon.stub()
        };
        next = sinon.stub();
        global.sequelizeModels = {
            MatchingSample: {
                findAll() {
                },
            },
            X3PSampleFile: {
                findAll() {
                }
            },
            X3PExamFile: {
                findAll() {
                }
            }
        };

        matchingSampleFindAllStub = sinon.stub(global.sequelizeModels.MatchingSample, "findAll");
        sampleFileFindAllStub = sinon.stub(global.sequelizeModels.X3PSampleFile, "findAll");
        examFileFindAllStub = sinon.stub(global.sequelizeModels.X3PExamFile, "findAll");
        sinon.stub(errorFactory, "notFound");
    });

    afterEach(() => {
        sinon.restore();
    });

    it('should return 404 if not found activity', async () => {
        let dbError = new Error("Database error")
        matchingSampleFindAllStub.rejects(dbError)

        await matchingSampleController.exportX3p(req, res, next)

        expect(next.calledOnce).to.be.true
        expect(next.calledWith(dbError)).to.be.true
    })

    it('should return 500 if no x3p files found', async () => {
        matchingSampleFindAllStub.resolves([])
        sampleFileFindAllStub.resolves([])
        examFileFindAllStub.resolves([])

        await matchingSampleController.exportX3p(req, res, next)

        expect(next.calledOnce).to.be.true
    })

    it('should return 200 if request valid', async () => {
        matchingSampleFindAllStub.resolves([{examId: 1, sampleId: 1}])
        sampleFileFindAllStub.resolves([{fileName: "fileName", fileHash: "fileHash"}])
        examFileFindAllStub.resolves([{fileName: "fileName", fileHash: "fileHash"}])
        sinon.stub(CommonUtil, "dateFormat").resolves("date")
        sinon.stub(CryptoJS, "MD5").resolves("md5")
        sinon.stub(ZipUtil, "zipFilesToFolder").resolves({zipFilePath: "zipFilePath"})

        await matchingSampleController.exportX3p(req, res, next)

        expect(res.download.calledWith("zipFilePath")).to.be.true
    })

})

describe('exportPdf', () => {
    let req, res, next
    let matchingSampleFindAllStub, sampleFindAllStub, examFindAllStub

    beforeEach(() => {
        req = {
            query: {
                activityIds: [1],
                matchStatus: ["matchStatus"],
                type: ["type"],
                crime: ["crime"],
                calibre: ["calibre"]
            },
            user: {id: 1}
        };
        res = {
            download: sinon.stub()
        };
        next = sinon.stub();
        global.sequelizeModels = {
            MatchingSample: {
                findAll() {
                },
            },
            X3PSample: {
                findAll() {
                }
            },
            X3PExam: {
                findAll() {
                }
            }
        };

        matchingSampleFindAllStub = sinon.stub(global.sequelizeModels.MatchingSample, "findAll");
        sampleFindAllStub = sinon.stub(global.sequelizeModels.X3PSample, "findAll");
        examFindAllStub = sinon.stub(global.sequelizeModels.X3PExam, "findAll");
        sinon.stub(errorFactory, "notFound");
    });

    afterEach(() => {
        sinon.restore();
    });

    it('should return 500 if createPdfKitDocument error', async () => {
        matchingSampleFindAllStub.resolves([])
        sampleFindAllStub.resolves([])
        examFindAllStub.resolves([{
            id: 1,
            type: 'exam',
            artefactType: "artefactType",
            metadata: {
                fileHash: "fileHash",
                name: "name",
                description: "description",
                crime: "crime",
                recoveryLocation: "recoveryLocation",
                occurrenceDate: "occurrenceDate",
                calibre: "calibre",
                numberOfLandsAndGrooves: "numberOfLandsAndGrooves",
                directionOfLandsAndGrooves: "directionOfLandsAndGrooves",
                riflingManufacturing: "riflingManufacturing",
                manufacturingMaterial: "manufacturingMaterial"
            }
        }])

        await matchingSampleController.exportPdf(req, res, next)

        expect(next.calledOnce).to.be.true
    })

    it('should return 200 if request valid', async () => {
        matchingSampleFindAllStub.resolves([{examId: 1, sampleId: 1, matchStatus: "matchStatus"}])
        sampleFindAllStub.resolves([])
        examFindAllStub.resolves([{
            id: 1,
            type: 'exam',
            artefactType: "artefactType",
            metadata: {
                fileHash: "fileHash",
                name: "name",
                description: "description",
                crime: "crime",
                recoveryLocation: "recoveryLocation",
                occurrenceDate: "occurrenceDate",
                calibre: "calibre",
                numberOfLandsAndGrooves: "numberOfLandsAndGrooves",
                directionOfLandsAndGrooves: "directionOfLandsAndGrooves",
                riflingManufacturing: "riflingManufacturing",
                manufacturingMaterial: "manufacturingMaterial"
            }
        }])

        await matchingSampleController.exportPdf(req, res, next)
    })

})