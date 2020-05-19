You can use the sandbox environment to <a href="/api-documentation/docs/testing">test this API</a>. 

## Test headers
Additional test support is provided by a number of test headers that can be 
passed when submitting new or amended ENS declarations.

The following test headers are supported:

### _simulateRiskingResponse_
This header should be used in order for risking simulation to occur so that an outcome 
(see [Safety and Security Import Outcomes](/api-documentation/docs/api/service/import-control-entry-declaration-outcome/1.0))
is made available for an ENS submission.

The following values are supported:

<table>
    <thead>
        <tr>
            <th>Value</th>    
            <th>Description</th>
        </tr>
    </thead>
    <tbody>
        <tr>
            <td>accept</td>    
            <td>Associate a positive outcome and a Movement Reference Number with an ENS submission (identifiable by its correlation ID).</td>
        </tr>
        <tr>
            <td>reject</td>    
            <td>Associate a negative outcome and error details with an ENS submission (identifiable by its correlation ID).</td>
        </tr>
    </tbody>
</table>
<br/>
If the header is omitted or has any other value then no 
risking simulation will be performed and no outcome
will be made available for an ENS submission.
<br/><br/>

### _riskingResponseError_
To control the specific error received with a negative outcome when the
_simulateRiskingResponse_ header is set to _reject_. 

The following values are supported:

<table>
    <thead>
        <tr>
            <th>Value</th>    
            <th>Description</th>
        </tr>
    </thead>
    <tbody>
        <tr>
            <td>nonUniqueLRN</td>
            <td>Simulates the scenario where the local reference number in the declaration is not unique.</td>
        </tr>
        <tr>
            <td>badTransportMode</td>
            <td>Simulates the scenario where the transport mode is not supported.</td>
        </tr>
        <tr>
            <td>badMessageCode</td>
            <td>Simulates the scenario where the message code is not supported.</td>
        </tr>
    </tbody>
</table>
<br/>
If the _simulateRiskingResponse_ header is set to _reject_
but no _riskingResponseError_ header is provided, a default of _badTransportMode_ is assumed.
<br/><br/>

### _simulateRiskingResponseLatencyMillis_
This is used with the _simulateRiskingResponse_ header
to provide a delay between the submission of an ENS and the outcome being made available.

The header value is the required simulated latency in milliseconds. 

If a value larger than 30000 is supplied, then 30 seconds will be assumed.
If the header or value is omitted, then no latency will be simulated.
<br/><br/>

### _simulateInterventionResponse_
This header should be used in order for intervention simulation to occur so that an advanced notification
(see [Safety and Security Import Notifications](/api-documentation/docs/api/service/import-control-entry-declaration-intervention/1.0))
is made available for an ENS submission.

The header takes the values _true_ or _false_. 

When _true_, an advanced notification will be associated with an ENS submission
(identifiable by its correlation ID).

If the header is omitted or has any other value then no 
intervention simulation will be performed, and no advanced notification
will be made available for an ENS submission.
<br/><br/>

### _simulateInterventionResponseLatencyMillis_
This is used with the  _simulateInterventionResponse_ header
to provide a delay between the submission of an ENS and the advanced
notification being made available.

The header value is the required simulated latency in milliseconds. 

If a value larger than 30000 is supplied, then 30 seconds will be assumed.
If the header or value is omitted, then no latency will be simulated. 
<br/><br/>

If you have a specific testing need that is not supported in the sandbox, contact 
<a href="/developer/support">our support team</a>.
