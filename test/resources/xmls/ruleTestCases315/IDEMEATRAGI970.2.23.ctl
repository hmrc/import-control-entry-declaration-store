<?xml version="1.0" encoding="UTF-8"?>
<!--
  Test case control file
  -->
<err:ErrorResponse xmlns:err="http://www.govtalk.gov.uk/CM/errorresponse" xmlns:dsl="http://decisionsoft.com/rim/errorExtension" SchemaVersion="2.0">
  <err:Error>
    <err:RaisedBy>ChRIS</err:RaisedBy>
    <err:Number>8112</err:Number>
    <err:Type>business</err:Type>
    <err:Text>If first digit of 'Specific Circumstance Indicator' is 'D' THEN 'Transport Mode at Border' cannot be '1', '3', '4', '8', '10' or '11' (C529)</err:Text>
    <err:Location>/ie:CC315A[1]/HEAHEA[1]</err:Location>
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
    <err:Number>8117</err:Number>
    <err:Type>business</err:Type>
    <err:Text>The 'Total Number of Packages' is equal to the sum of all 'Number of Packages' + all 'Number of pieces' + a value of '1' for each declared 'bulk' (R105)</err:Text>
    <err:Location>/ie:CC315A[1]/HEAHEA[1]</err:Location>
  </err:Error>
  <err:Error>
    <err:RaisedBy>ChRIS</err:RaisedBy>
    <err:Number>8206</err:Number>
    <err:Type>business</err:Type>
    <err:Text>[Total number of items] must equal the number of [Goods item] present.</err:Text>
    <err:Location>/ie:CC315A[1]/HEAHEA[1]</err:Location>
  </err:Error>
</err:ErrorResponse>
