Just a personal freemind flash browser.
It was developed using FlashDevelop from http://www.flashdevelop.org/

USE:
 - insert in any browser page like in the example.

 CONFIGURATION:
	All this variables can be added in the script. None of then if needed, they all
	have default values.

	//Where to open a link: 
	//default="_self"
		fo.addVariable("openUrl", "_self");

	// IF we want to initiate de freemind with al the nodes collapset from this level
	// =default "-1" that means, do nothing
		fo.addVariable("startCollapsedToLevel","1");

	// Initial mindmap to load
	// default="index.mm"
		fo.addVariable("initLoadFile", "index.mm");

	// Map background color
	// default=last chosen by user or white
		fo.addVariable("bgcolor", 0xEEFFEE);
