# tagSubscriptionUtils
Scripts functions for tags subscription and dataset update

<b>Project example :<br></b>
See in root directory : <b>TagsSubscriptions_Examples_2017-03-22.zip</b>

<b>Ignition utils modules :<br></b>
See in root directory : <b>CommunityUtils-signed-1.0.5.modl</b>

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
