# tagSubscriptionUtils
Scripts functions for tags subscription and dataset update

<b>Project example :<br></b>
See in root directory : <b>TagsSubscriptions_Examples_2017-03-22.zip</b>

<b>Ignition utils modules :<br></b>
See in root directory : <b>CommunityUtils-signed-1.0.6.modl</b>

More informations :
http://smartindustrialcomputing.blogspot.fr/2017/03/ignition-utils-module-clients-scripts.html

This modules enables subscription in 2 way :
1.	An input list of tag path
2.	An input dataset with a tag path columns, and other columns for tag's Value, Timestamp, Quality

In return, the module write a client tag (typed dataset) with:
1.	tags data with Value, Timestamp, Quality updated
2.	input dataset with Value, Timestamp, Quality updated

As the result, in <b>use case 1</b>, you can quickly create an efficient tag browser based on:<br>
*	this utils modules<br>
*	a tag browser tree<br>
*	a power table (binded to the tag client updated by this module)<br>

![tagbrowser](/tagbrowser.jpg)

Use <b>case 2</b>, an input dataset with columns V, T, Q automatically refreshed when the tags' data change.
Button refresh can only be use to force a refresh.

![tagbrowser](/dataset.jpg)

Hope this help you to build more reactive HMI with Ignition an its power table component !

</ul>
<p><b>ChangeLog : (Date format : YYYY/MM/DD)</b>
<ul>
    <li>1.0.0 : 2017/03/10<br>
        <ul><li>First release</li></ul>
    <li>1.0.1 : 2017/03/14<br>
        <ul><li>.dataset.subscribeWK for keywords style parameters : index of V,T,Q columns</li></ul>
        <ul><li>.dataset.subscribeWK / .dataset.subscribe : force columns types for V,T,Q if type is incorrect in input dataset</li></li></ul>
    <li>1.0.2 : 2017/03/17<br>
        <ul><li>support for tag array and dataset value</li></ul>
    <li>1.0.3 : 2017/03/20<br>
        <ul><li>Merge function dataset.subscribe and dataset.subscribeWK</li></ul>
    <li>1.0.4 : 2017/03/21<br>
        <ul><li>Add a comma between rows for dataset String formatting</li></ul>
        <ul><li>Somes Improvments</li></ul>
        <ul><li>Add functions freeze/unfreeze subscription :</li></ul>
        <ul><li>freezeAll</li></ul>
        <ul><li>unfreezeAll</li></ul>
        <ul><li>getSubscribedFullTagPathsList</li></ul>
        <ul><li>getSubscribedTagPathsList</li></ul>
        <ul><li>.dataset.freezeAll</li></ul>
        <ul><li>.dataset.unfreezeAll</li></ul>
        <ul><li>.dataset.getSizeOfSubscribedTagsList</li></ul>
        <ul><li>.dataset.getSubscribedFullTagPathsList</li></ul>
        <ul><li>.dataset.getSubscribedTagPathsList</li></ul>
    <li>1.0.5 : 2017/03/27<br>
        <ul><li>Some StringBuilder improvments</li></ul>
    <li>1.0.6 : 2017/06/12<br>
        <ul><li>Add function tagsExists to check if a list of tags exists</li></ul>
</ul>
