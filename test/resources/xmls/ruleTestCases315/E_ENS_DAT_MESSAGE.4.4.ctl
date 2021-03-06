<?xml version="1.0" encoding="UTF-8"?>
<!--
  Test case control file
  -->
<err:ErrorResponse xmlns:err="http://www.govtalk.gov.uk/CM/errorresponse" xmlns:dsl="http://decisionsoft.com/rim/errorExtension" SchemaVersion="2.0">
  <err:Error>
    <err:RaisedBy>ChRIS</err:RaisedBy>
    <err:Number>8611</err:Number>
    <err:Type>business</err:Type>
    <err:Text>[Goods item] may occur up to 999 times</err:Text>
    <err:Location>/ie:CC315A[1]</err:Location>
  </err:Error>
  <err:Error>
    <err:RaisedBy>ChRIS</err:RaisedBy>
    <err:Number>8102</err:Number>
    <err:Type>business</err:Type>
    <err:Text>Each 'Item No.' (box 32) is unique throughout the declaration. The items shall be numbered in a sequential fashion, starting from '1' for the first item and incrementing the numbering by '1' for each following item. (R007)</err:Text>
    <err:Location>/ie:CC315A[1]</err:Location>
  </err:Error>
  <err:Error>
    <err:RaisedBy>ChRIS</err:RaisedBy>
    <err:Number>8207</err:Number>
    <err:Type>business</err:Type>
    <err:Text>The field '(Code) Commodity' is required if 'Goods Item Description' is not used (C585)</err:Text>
    <err:Location>/ie:CC315A[1]/GOOITEGDS[1000]</err:Location>
  </err:Error>
  <err:Error>
    <err:RaisedBy>ChRIS</err:RaisedBy>
    <err:Number>8206</err:Number>
    <err:Type>business</err:Type>
    <err:Text>[Total number of items] must equal the number of [Goods item] present.</err:Text>
    <err:Location>/ie:CC315A[1]/HEAHEA[1]</err:Location>
  </err:Error>
  <err:Error>
    <err:RaisedBy>ChRIS</err:RaisedBy>
    <err:Number>8199</err:Number>
    <err:Type>business</err:Type>
    <err:Text>'Identity of Means of Transport at Border (ex Box 21)' is required if 'Identity Crossing Border (box 21)' is not present and 'Transport Mode at Border (box 25)' does not equal '4'</err:Text>
    <err:Location>/ie:CC315A[1]/GOOITEGDS[1000]</err:Location>
  </err:Error>
</err:ErrorResponse>
