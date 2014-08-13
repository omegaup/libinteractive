package com.omegaup.libinteractive.target

import scala.collection.mutable.StringBuilder

import com.omegaup.libinteractive.idl._

class Java(idl: IDL, options: Options) extends Target(idl, options) {
	override def generateParent() = {
		throw new UnsupportedOperationException("generateParent")
	}

	override def generateChildren() = {
		throw new UnsupportedOperationException("generateChildren")
	}
}

/* vim: set noexpandtab: */
