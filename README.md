
# Import Control Entry Declaration Store

The Import Control Entry Declaration Store responsibilities:
- receive, validate and persist XML Entry Summary Declarations \<CC315> / IE315 and Amendments \<CC313> / IE313 as JSON to be used by later by C&IT and by other import control entry declaration microservices.

## Development Setup
- MongoDB instance
- Run locally: `sbt run` which runs on port `9818` by default
- Run all associated services: `sm --start ICED_ALL -f`
- Run with test end points: `sbt 'run -Dapplication.router=testOnlyDoNotUseInAppConf.Routes'`

## Tests
Run Unit Tests: `sbt test`

Run Integration Tests: `sbt it:test`

Run Acceptance Tests: [Instructions here](https://github.com/hmrc/import-control-entry-declaration-api-acceptance-tests)

Run Performance Tests: [Instructions here](https://github.com/hmrc/import-control-entry-declaration-api-performance-tests)

## API

|Internal endpoint paths prefixed by `/import-control-entry-declaration-store` | Supported Methods | Type | Description |
| ----------------------------------------------------------| ----------------- | -----|----------- |
|```/```                                                    |        POST       | External | Endpoint for users to save IE315 xml to the database. |
|```/:mrn```                                                |        PUT        | External | Endpoint for users to save IE313 xml to the database. |
|```/import-control/entry-summary-declaration/:id```        |        GET        | Internal | Endpoint for C&IT to get an entry declaration from the database. |
|```/import-control/amendment/acceptance-enrichment/:id```  |        GET        | Internal | Endpoint for [Decision microservice](https://github.com/hmrc/import-control-entry-declaration-decision) to get an acceptance enrichment from the amendment in the database. |
|```/import-control/declaration/acceptance-enrichment/:id```|        GET        | Internal | Endpoint for [Decision microservice](https://github.com/hmrc/import-control-entry-declaration-decision) to get an acceptance enrichment from the declaration in the database. |
|```/import-control/amendment/rejection-enrichment/:id```   |        GET        | Internal | Endpoint for [Decision microservice](https://github.com/hmrc/import-control-entry-declaration-decision) to get an rejection enrichment from the amendment in the database. |
|```/import-control/test-only/submission-ids/:eori/:correlationId``` |        GET        | Test | Endpoint to get submission Id from EORI and Correlation Id |

## API Reference / Documentation 
For more information on external API endpoints see the RAML at [Developer Hub] or using the endpoint below

|Path                          | Supported Methods | Description |
| -----------------------------| ----------------- | ----------- |
|```/api/conf/:version/*file```|        GET        | /api/conf/1.0/application.raml |
|```/api/conf/:version/rules/315.md```|        GET        | Returns ENS (315) Level 2 validation rules as markdown |
|```/api/conf/:version/rules/313.md```|        GET        | Returns new ENS amendment (313) Level 2 validation rules as markdown |

## License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")
