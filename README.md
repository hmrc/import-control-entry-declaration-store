
# Import Control Entry Declaration Store

The Import Control Entry Declaration Store responsibilities:
- receive, validate and persist XML Entry Summary Declarations \<CC315> / IE315 and Amendments \<CC313> / IE313 as JSON to be used by later by C&IT and by other import control entry declaration microservices.

## Development Setup
- MongoDB instance
- Run locally: `sbt run` which runs on port `9818` by default
- Run with test end points: `sbt 'run -Dplay.http.router=testOnlyDoNotUseInAppConf.Routes'`

## Tests
Run Unit Tests: `sbt test`

Run Integration Tests: `sbt it:test`

## API

| Path | Supported Methods | Type | Description |
| ----------------------------------------------------------| ----------------- | -----| ------------|
|```/```                                                    |        POST       | External | Endpoint for users to save IE315 xml to the database. |
|```/:mrn```                                                |        PUT        | External | Endpoint for users to save IE313 xml to the database. |
|```/import-control/entry-summary-declaration/:id```        |        GET        | Internal | Endpoint for C&IT to get an entry declaration from the database. |
|```/import-control/amendment/acceptance-enrichment/:id```  |        GET        | Internal | Endpoint for [Decision microservice](https://github.com/hmrc/import-control-entry-declaration-decision) to get an acceptance enrichment from the amendment in the database. |
|```/import-control/declaration/acceptance-enrichment/:id```|        GET        | Internal | Endpoint for [Decision microservice](https://github.com/hmrc/import-control-entry-declaration-decision) to get an acceptance enrichment from the declaration in the database. |
|```/import-control/amendment/rejection-enrichment/:id```   |        GET        | Internal | Endpoint for [Decision microservice](https://github.com/hmrc/import-control-entry-declaration-decision) to get an rejection enrichment from the amendment in the database. |
|```/import-control/declaration/rejection-enrichment/:id``` |        GET        | Internal | Endpoint for [Decision microservice](https://github.com/hmrc/import-control-entry-declaration-decision) to get an rejection enrichment from the declaration in the database. |
|```/import-control/replay-batch```                         |        POST       | Internal | Endpoint to replay messages to C&IT from the database. |
|```/import-control/housekeeping/status```                  |        GET        | Internal | Endpoint to get housekeeping status. |
|```/import-control/housekeeping/status```                  |        PUT        | Internal | Endpoint to set housekeeping status. |
|```/import-control/housekeeping/submissionid/:submissionId```|        PUT        | Internal | Endpoint to set a short ttl on a specified record. |
|```/import-control/housekeeping/eoriandcorrelationid/:eori/:correlationId```|        PUT        | Internal | Endpoint to set a short ttl on a specified record. |
|```/import-control/traffic-switch```                      |        GET        | Internal | Endpoint to get the traffic switch status. |
|```/import-control/traffic-switch/start```                |        PUT        | Internal | Endpoint to start the traffic flowing to EIS. |
|```/import-control/test-only/submission-ids/:eori/:correlationId``` | GET      | Test | Endpoint to get submission Id from EORI and Correlation Id. |
|```/import-control/test-only/traffic-switch/stop```       |        PUT        | Test | Endpoint to stop the traffic flowing to EIS. |
|```/import-control/test-only/traffic-switch/reset```      |        PUT        | Test | Endpoint to reset the traffic switch to initial state. |

## API Reference / Documentation 
For more information on external API endpoints see the YAML at [Developer Hub]("https://developer.service.hmrc.gov.uk/api-documentation/docs/api/service/import-control-entry-declaration-store/1.0") or using the endpoint below

|Path                          | Supported Methods | Description                                                          |
| -----------------------------| ----------------- |----------------------------------------------------------------------|
|```/api/conf/:version/*file```|        GET        | /api/conf/1.0/application.yaml                                       |
|```/api/conf/:version/rules/315.md```|        GET        | Returns ENS (315) Level 2 validation rules as markdown               |
|```/api/conf/:version/rules/313.md```|        GET        | Returns new ENS amendment (313) Level 2 validation rules as markdown |

## License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")
