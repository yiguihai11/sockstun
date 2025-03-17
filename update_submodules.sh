#!/bin/bash

# 切换到主仓库根目录
cd "$(git rev-parse --show-toplevel)"

# 读取 .gitmodules 中的子模块路径
submodules=$(git config --file .gitmodules --get-regexp path | awk '{ print $2 }')

# 遍历每个子模块
for submodule in $submodules; do
    echo "正在更新子模块: $submodule"
    
    # 进入子模块目录
    cd "$submodule" || continue
    
    # 获取远程默认分支（通常是 master 或 main）
    default_branch=$(git remote show origin | grep "HEAD branch" | awk '{print $3}')
    if [ -z "$default_branch" ]; then
        echo "警告: 无法确定 $submodule 的默认分支，跳过此子模块。"
        cd - > /dev/null
        continue
    fi
    
    # 切换到默认分支
    git checkout "$default_branch"
    
    # 拉取最新代码
    git pull origin "$default_branch"
    
    # 返回主仓库目录
    cd - > /dev/null
done

# 返回主仓库根目录
cd "$(git rev-parse --show-toplevel)"

# 添加所有子模块的更新
git add $(echo $submodules)

# 提交更改
git commit -m "更新子模块到最新版本"

# （可选）推送主仓库
git push origin master && echo "所有子模块已更新并提交。"