name_change_voucher = {
	use = async(function(player)
		local item = player:getInventoryItem(player.invSlot)
		local oldName = player.name

		local t = {graphic = item.icon, color = item.iconC}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0

		if not player:canCast(1, 1, 0) then
			player:sendMinitext("Kau tidak bisa memakai ini.")
			return
		end

		local name = player:inputLetterCheck(player:input("Kau ingin namamu menjadi apa?"))

		if string.len(name) < 3 then
			player:dialogSeq(
				{t, "Kau tidak bisa memasukkan nama yang kurang dari 3 huruf."},
				0
			)
			return
		end
		if string.len(name) > 12 then
			player:dialogSeq(
				{t, "Kau tidak bisa memasukkan nama yang lebih dari 12 huruf."},
				0
			)
			return
		end

		local nameCheck = getOfflineID(name)

		if nameCheck ~= false then
			player:dialogSeq(
				{t, "Nama itu sudah ada. Coba nama lain."},
				0
			)
			return
		end

		local confirm = player:menuSeq(
			"Kau yakin ingin " .. name .. " menjadi nama barumu?",
			{"Ya", "Tidak"},
			{}
		)

		if confirm == 1 then
			if player:hasItem("name_change_voucher", 1) ~= true then
				player:dialogSeq({t, "Vouchernya tidak ada padamu."}, 0)
				return
			end

			player:removeItem("name_change_voucher", 1)
			player.name = name
			player:updateState()
			player:updateMail(oldName)
			characterLog.nameChangeWrite(player, oldName)

			player:dialogSeq({t, "Masuk ulang untuk memperbarui karaktermu."}, 0)
		end
	end)
}
