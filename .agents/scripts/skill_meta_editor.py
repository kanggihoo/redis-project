import os
import re
import argparse

def update_frontmatter(file_path, updates):
    """
    Updates the YAML frontmatter of a single file with dynamic key-value pairs.
    """
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()

        # YAML 프론트매터 추출 (--- 사이의 내용)
        match = re.search(r'^---\s*\n(.*?)\n---\s*\n', content, re.DOTALL)
        if not match:
            print(f"⚠️ Skip: No frontmatter found in {file_path}")
            return False

        frontmatter = match.group(1)
        body = content[match.end():]
        
        lines = frontmatter.splitlines()
        new_lines = []
        applied_keys = set()
        
        # 기존 라인을 순회하며 업데이트할 키가 있는지 확인
        for line in lines:
            stripped = line.strip()
            # 주석이 아니고 ':'가 포함된 라인만 처리
            if ':' in stripped and not stripped.startswith('#'):
                key = stripped.split(':', 1)[0].strip()
                if key in updates:
                    # 새로운 값으로 교체
                    new_lines.append(f"{key}: {updates[key]}")
                    applied_keys.add(key)
                    continue
            new_lines.append(line)
        
        # 기존에 없던 새로운 키들 추가
        for key, value in updates.items():
            if key not in applied_keys:
                new_lines.append(f"{key}: {value}")
                
        new_frontmatter = '\n'.join(new_lines)
        new_content = f'---\n{new_frontmatter}\n---\n{body}'
        
        with open(file_path, 'w', encoding='utf-8') as f:
            f.write(new_content)
        return True
    except Exception as e:
        print(f"❌ Error processing {file_path}: {e}")
        return False

def main():
    parser = argparse.ArgumentParser(description="Skill Meta Editor: Update SKILL.md frontmatter dynamically.")
    # nargs='+'를 사용하여 하나 이상의 경로를 받을 수 있게 함
    parser.add_argument("--path", nargs="+", required=True, help="One or more directories or SKILL.md file paths to update")
    parser.add_argument("updates", nargs="*", help="Key-value pairs to update (format: key=value)")
    
    args = parser.parse_args()
    
    # updates 인자를 딕셔너리로 변환
    update_dict = {}
    for item in args.updates:
        if '=' in item:
            key, value = item.split('=', 1)
            update_dict[key.strip()] = value.strip()
    
    if not update_dict:
        print("⚠️ No updates provided. Use key=value format.")
        return

    # 모든 경로에 대해 파일 목록 생성
    files_to_process = []
    for target in args.path:
        if os.path.isfile(target) and target.endswith('SKILL.md'):
            files_to_process.append(target)
        elif os.path.isdir(target):
            for root, dirs, files in os.walk(target):
                # templates 디렉토리는 기본적으로 제외 (프로젝트 규칙)
                if 'templates' in dirs:
                    dirs.remove('templates')
                if 'SKILL.md' in files:
                    files_to_process.append(os.path.join(root, 'SKILL.md'))
        else:
            print(f"⚠️ Warning: {target} is not a valid path and will be skipped.")

    if not files_to_process:
        print("❌ No valid SKILL.md files found to process.")
        return

    # 일괄 처리
    success_count = 0
    for file_path in files_to_process:
        if update_frontmatter(file_path, update_dict):
            print(f"✅ Updated: {file_path}")
            success_count += 1

    print(f"\nSuccessfully updated {success_count} files across {len(args.path)} target paths.")

if __name__ == "__main__":
    main()
