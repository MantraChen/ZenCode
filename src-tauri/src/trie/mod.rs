pub mod keywords;

use std::collections::HashMap;

/// Trie 节点定义
/// 每个节点包含子节点映射和是否为完整关键字的标记
#[derive(Debug, Default)]
pub struct TrieNode {
    children: HashMap<char, TrieNode>,
    is_end: bool,
}

/// 字典树 —— 毫秒级前缀补全引擎
#[derive(Debug, Default)]
pub struct Trie {
    root: TrieNode,
}

impl Trie {
    pub fn new() -> Self {
        Trie {
            root: TrieNode::default(),
        }
    }

    /// 插入一个关键字
    pub fn insert(&mut self, word: &str) {
        let mut node = &mut self.root;
        for ch in word.chars() {
            node = node.children.entry(ch).or_default();
        }
        node.is_end = true;
    }

    /// 批量插入
    pub fn insert_many(&mut self, words: &[&str]) {
        for word in words {
            self.insert(word);
        }
    }

    /// 根据前缀搜索，返回所有匹配的完整关键字
    pub fn search_prefix(&self, prefix: &str) -> Vec<String> {
        let mut node = &self.root;

        // 先沿前缀走到对应节点
        for ch in prefix.chars() {
            match node.children.get(&ch) {
                Some(child) => node = child,
                None => return vec![], // 前缀不存在，直接返回空
            }
        }

        // 从该节点 DFS 收集所有完整词
        let mut results = Vec::new();
        let mut current_word = prefix.to_string();
        Self::collect(node, &mut current_word, &mut results);
        results
    }

    /// DFS 递归收集所有以当前路径为前缀的完整词
    fn collect(node: &TrieNode, current: &mut String, results: &mut Vec<String>) {
        if node.is_end {
            results.push(current.clone());
        }
        // 按字母序遍历，保证输出有序
        let mut keys: Vec<char> = node.children.keys().copied().collect();
        keys.sort_unstable();
        for ch in keys {
            current.push(ch);
            Self::collect(&node.children[&ch], current, results);
            current.pop();
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_basic_search() {
        let mut trie = Trie::new();
        trie.insert_many(&["if", "int", "include", "import", "in"]);

        let results = trie.search_prefix("in");
        assert_eq!(results, vec!["in", "include", "int"]);

        let results = trie.search_prefix("inc");
        assert_eq!(results, vec!["include"]);

        let results = trie.search_prefix("z");
        assert!(results.is_empty());
    }

    #[test]
    fn test_empty_prefix_returns_all() {
        let mut trie = Trie::new();
        trie.insert_many(&["for", "func", "false"]);
        let results = trie.search_prefix("f");
        assert_eq!(results, vec!["false", "for", "func"]);
    }
}
