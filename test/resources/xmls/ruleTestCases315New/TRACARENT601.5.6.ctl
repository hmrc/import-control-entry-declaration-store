<?xml version="1.0" encoding="UTF-8"?>
<!--
  Test case control file
  -->
<err:ErrorResponse xmlns:err="http://www.govtalk.gov.uk/CM/errorresponse" xmlns:dsl="http://decisionsoft.com/rim/errorExtension" SchemaVersion="2.0">
  <err:Error>
    <err:RaisedBy>ChRIS</err:RaisedBy>
    <err:Number>8164</err:Number>
    <err:Type>business</err:Type>
    <err:Text>The TIN is required if 'Transport mode at border' = '1', '4', '8', '10', or '11' (C502)</err:Text>
    <err:Location>/ie:CC315A[1]/TRACARENT601[1]</err:Location>
  </err:Error>
  <err:Error>
    <err:RaisedBy>ChRIS</err:RaisedBy>
    <err:Number>8159</err:Number>
    <err:Type>business</err:Type>
    <err:Text>The field 'Name' within Trader at Entry (Carrier) is required if no TIN has been declared (C501)</err:Text>
    <err:Location>/ie:CC315A[1]/TRACARENT601[1]</err:Location>
  </err:Error>
  <err:Error>
    <err:RaisedBy>ChRIS</err:RaisedBy>
    <err:Number>8160</err:Number>
    <err:Type>business</err:Type>
    <err:Text>The field 'Street and Number' within Trader at Entry (Carrier) is required if no TIN has been declared (C501)</err:Text>
    <err:Location>/ie:CC315A[1]/TRACARENT601[1]</err:Location>
  </err:Error>
  <err:Error>
    <err:RaisedBy>ChRIS</err:RaisedBy>
    <err:Number>8161</err:Number>
    <err:Type>business</err:Type>
    <err:Text>The field 'Postcode' within Trader at Entry (Carrier) is required if no TIN has been declared (C501)</err:Text>
    <err:Location>/ie:CC315A[1]/TRACARENT601[1]</err:Location>
  </err:Error>
  <err:Error>
    <err:RaisedBy>ChRIS</err:RaisedBy>
    <err:Number>8162</err:Number>
    <err:Type>business</err:Type>
    <err:Text>The field 'City' within Trader at Entry (Carrier) is required if no TIN has been declared (C501)</err:Text>
    <err:Location>/ie:CC315A[1]/TRACARENT601[1]</err:Location>
  </err:Error>
  <err:Error>
    <err:RaisedBy>ChRIS</err:RaisedBy>
    <err:Number>8163</err:Number>
    <err:Type>business</err:Type>
    <err:Text>The field 'Country Code' within Trader at Entry (Carrier) is required if no TIN has been declared (C501)</err:Text>
    <err:Location>/ie:CC315A[1]/TRACARENT601[1]</err:Location>
  </err:Error>
  <err:Error>
    <err:RaisedBy>ChRIS</err:RaisedBy>
    <err:Number>8118</err:Number>
    <err:Type>business</err:Type>
    <err:Text>If 'Transport Mode at Border = '4' Then 'Conveyance Reference Number' consists of the (IATA) flight number and has format an..8 of which an..3 is the mandatory prefix identifying the airline/operator: n..4 is the mandatory number of the flight and a1 is the optional suffix (TR0518)</err:Text>
    <err:Location>/ie:CC315A[1]/HEAHEA[1]</err:Location>
  </err:Error>
  <err:Error>
    <err:RaisedBy>ChRIS</err:RaisedBy>
    <err:Number>8147</err:Number>
    <err:Type>business</err:Type>
    <err:Text>The field 'Nationality crossing border (ex. Box 21)' is required if 'Transport mode at border (box 25)' does not equal '2' (C020)</err:Text>
    <err:Location>/ie:CC315A[1]/GOOITEGDS[1]/IDEMEATRAGI970[1]</err:Location>
  </err:Error>
  <err:Error>
    <err:RaisedBy>ChRIS</err:RaisedBy>
    <err:Number>8198</err:Number>
    <err:Type>business</err:Type>
    <err:Text>Identity of Means of Transport at Border (ex Box 21)' can not be used if 'Identity crossing border (box 21)' is present OR 'Transport Mode at Border (box 25)' = '4' (C019)</err:Text>
    <err:Location>/ie:CC315A[1]/GOOITEGDS[1]/IDEMEATRAGI970[1]</err:Location>
  </err:Error>
  <err:Error>
    <err:RaisedBy>ChRIS</err:RaisedBy>
    <err:Number>8112</err:Number>
    <err:Type>business</err:Type>
    <err:Text>If first digit of 'Specific Circumstance Indicator' is 'D' THEN 'Transport Mode at Border' cannot be '1', '3', '4', '8', '10' or '11' (C529)</err:Text>
    <err:Location>/ie:CC315A[1]/HEAHEA[1]</err:Location>
  </err:Error>
</err:ErrorResponse>
