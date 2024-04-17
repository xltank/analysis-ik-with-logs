/**
 * IK 中文分词  版本 5.0
 * IK Analyzer release 5.0
 * 
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * 源代码由林良益(linliangyi2005@gmail.com)提供
 * 版权声明 2012，乌龙茶工作室
 * provided by Linliangyi and copyright 2012 by Oolong studio
 * 
 */
package org.wltea.analyzer.core;

import java.util.Stack;
import java.util.TreeSet;

/**
 * IK分词歧义裁决器
 */
class IKArbitrator {

	IKArbitrator() {

	}

	AnalyzeContext context;

	String getLexemeString(Lexeme l) {
		int startPos = Math.min(l.getBeginPosition(), context.getSegmentBuff().length);
		int endPos = Math.min(l.getEndPosition(), context.getSegmentBuff().length);
		endPos = Math.max(startPos, endPos);
		return new String(this.context.getSegmentBuff()).substring(startPos, endPos);
	}

	String printLexemePath(String prefix, LexemePath path) {
		String s = "";
		QuickSortSet.Cell c = path.getHead();
		while (c != null) {
			Lexeme l = c.getLexeme();
			s += getLexemeString(l) + ", ";
			c = c.getNext();
		}
		String out = prefix + " lexemePath: " + s;
		System.out.println(out);
		return out;
	}
	
	void printTreeSet(String prefix, TreeSet<LexemePath> pathOptions) {
		System.out.println("printTreeSet: ");
		for (LexemePath p : pathOptions) {
			printLexemePath(prefix, p);
		}
	}

	/**
	 * 分词歧义处理
	 * // * @param orgLexemes
	 * 
	 * @param useSmart
	 */
	void process(AnalyzeContext context, boolean useSmart) {
		this.context = context;
		System.out.println("IKArbitrator.process()");
		QuickSortSet orgLexemes = context.getOrgLexemes();
		Lexeme orgLexeme = orgLexemes.pollFirst();
		int startPos = Math.min(orgLexeme.getBeginPosition(), context.getSegmentBuff().length);
		int endPos = Math.min(orgLexeme.getEndPosition(), context.getSegmentBuff().length);
		endPos = Math.max(startPos, endPos);
		System.out.println("IKArbitrator.process() orgLexeme: "+ new String(context.getSegmentBuff()).substring(startPos, endPos));
		LexemePath crossPath = new LexemePath();
		while (orgLexeme != null) {
			if (!crossPath.addCrossLexeme(orgLexeme)) {
				this.printLexemePath("IKArbitrator.process() addCrossLexeme:", crossPath);
				// 找到与crossPath不相交的下一个crossPath
				if (crossPath.size() == 1 || !useSmart) {
					// crossPath没有歧义 或者 不做歧义处理
					// 直接输出当前crossPath
					System.out.println("IKArbitrator.process() crossPath没有歧义 或者 不做歧义处理");
					context.addLexemePath(crossPath);
				} else {
					// 对当前的crossPath进行歧义处理
					QuickSortSet.Cell headCell = crossPath.getHead();
					LexemePath judgeResult = this.judge(headCell, crossPath.getPathLength());
					// 输出歧义处理结果judgeResult
					this.printLexemePath("IKArbitrator.process() 歧义处理结果:", judgeResult);
					context.addLexemePath(judgeResult);
				}

				// 把orgLexeme加入新的crossPath中
				crossPath = new LexemePath();
				crossPath.addCrossLexeme(orgLexeme);
			}
			orgLexeme = orgLexemes.pollFirst();
		}

		// 处理最后的path
		if (crossPath.size() == 1 || !useSmart) {
			// crossPath没有歧义 或者 不做歧义处理
			// 直接输出当前crossPath
			this.printLexemePath("IKArbitrator.process() 处理最后的 path:", crossPath);
			context.addLexemePath(crossPath);
		} else {
			// 对当前的crossPath进行歧义处理
			QuickSortSet.Cell headCell = crossPath.getHead();
			LexemePath judgeResult = this.judge(headCell, crossPath.getPathLength());
			// 输出歧义处理结果judgeResult
			context.addLexemePath(judgeResult);
		}
	}

	/**
	 * 歧义识别
	 * 
	 * @param lexemeCell     歧义路径链表头
	 * @param fullTextLength 歧义路径文本长度
	 * @return
	 */
	private LexemePath judge(QuickSortSet.Cell lexemeCell, int fullTextLength) {
		// 候选路径集合
		TreeSet<LexemePath> pathOptions = new TreeSet<LexemePath>();
		// 候选结果路径
		LexemePath option = new LexemePath();

		// 对crossPath进行一次遍历,同时返回本次遍历中有冲突的Lexeme栈
		Stack<QuickSortSet.Cell> lexemeStack = this.forwardPath(lexemeCell, option);

		printLexemePath("judge() 1,", option);
		// 当前词元链并非最理想的，加入候选路径集合
		pathOptions.add(option.copy());
		printTreeSet("judge() 1, pathOptions:", pathOptions);

		// 存在歧义词，处理
		QuickSortSet.Cell c = null;
		while (!lexemeStack.isEmpty()) {
			c = lexemeStack.pop();
			System.out.println("judge() while:"+getLexemeString(c.getLexeme()));
			// 回滚词元链
			this.backPath(c.getLexeme(), option);
			// 从歧义词位置开始，递归，生成可选方案
			this.forwardPath(c, option);
			printLexemePath("judge() 2,", option);
			pathOptions.add(option.copy());
			printTreeSet("judge() 2, pathOptions:", pathOptions);
		}

		// 返回集合中的最优方案
		return pathOptions.first();

	}

	/**
	 * 向前遍历，添加词元，构造一个无歧义词元组合
	 * // * @param LexemePath path
	 * 
	 * @return
	 */
	private Stack<QuickSortSet.Cell> forwardPath(QuickSortSet.Cell lexemeCell, LexemePath option) {
		// 发生冲突的Lexeme栈
		Stack<QuickSortSet.Cell> conflictStack = new Stack<QuickSortSet.Cell>();
		QuickSortSet.Cell c = lexemeCell;
		// 迭代遍历Lexeme链表
		while (c != null && c.getLexeme() != null) {
			if (!option.addNotCrossLexeme(c.getLexeme())) {
				// 词元交叉，添加失败则加入lexemeStack栈
				conflictStack.push(c);
			}
			c = c.getNext();
		}
		return conflictStack;
	}

	/**
	 * 回滚词元链，直到它能够接受指定的词元
	 * // * @param lexeme
	 * 
	 * @param l
	 */
	private void backPath(Lexeme l, LexemePath option) {
		while (option.checkCross(l)) {
			printLexemePath("backPath(), Lexeme:"+getLexemeString(l)+", path:", option);
			option.removeTail();
		}
	}
}
