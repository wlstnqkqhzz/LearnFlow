export function OrganizationFields({ code, name }: { code?: string; name?: string }) {
  return <>
    <label className="admin-field">코드<input name="code" required maxLength={50} pattern=".*\S.*" defaultValue={code} readOnly={code !== undefined} /><small>생성 후 코드는 변경할 수 없습니다.</small></label>
    <label className="admin-field">이름<input name="name" required maxLength={100} pattern=".*\S.*" defaultValue={name} /></label>
  </>
}
